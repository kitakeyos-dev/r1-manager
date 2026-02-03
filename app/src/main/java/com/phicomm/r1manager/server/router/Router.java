package com.phicomm.r1manager.server.router;

import com.phicomm.r1manager.util.AppLog;

import com.google.gson.Gson;
import com.phicomm.r1manager.server.annotation.DeleteMapping;
import com.phicomm.r1manager.server.annotation.GetMapping;
import com.phicomm.r1manager.server.annotation.PostMapping;
import com.phicomm.r1manager.server.annotation.RequestBody;
import com.phicomm.r1manager.server.annotation.RequestMapping;
import com.phicomm.r1manager.server.annotation.RequestParam;
import com.phicomm.r1manager.server.model.ApiResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

/**
 * Router - Dispatcher for annotation-based controllers
 */
public class Router {
    private static final String TAG = "Router";
    private static final Gson gson = new Gson();
    private final Map<String, RouteMethod> routes = new HashMap<String, RouteMethod>();

    public void registerController(Object controller) {
        Class<?> clazz = controller.getClass();
        String baseUri = "";

        if (clazz.isAnnotationPresent(RequestMapping.class)) {
            baseUri = clazz.getAnnotation(RequestMapping.class).value();
        }

        for (Method method : clazz.getDeclaredMethods()) {
            String subUri = null;
            String httpMethod = null;

            if (method.isAnnotationPresent(GetMapping.class)) {
                subUri = method.getAnnotation(GetMapping.class).value();
                httpMethod = "GET";
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                subUri = method.getAnnotation(PostMapping.class).value();
                httpMethod = "POST";
            } else if (method.isAnnotationPresent(DeleteMapping.class)) {
                subUri = method.getAnnotation(DeleteMapping.class).value();
                httpMethod = "DELETE";
            } else if (method.isAnnotationPresent(RequestMapping.class)) {
                subUri = method.getAnnotation(RequestMapping.class).value();
                httpMethod = "ANY";
            }

            if (subUri != null) {
                String fullUri = (baseUri + subUri).replaceAll("//+", "/");
                String key = httpMethod + ":" + fullUri;
                routes.put(key, new RouteMethod(controller, method));
                AppLog.d(TAG, "Registered route: " + key);
            }
        }
    }

    public NanoHTTPD.Response handle(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().name();

        String key = method + ":" + uri;
        RouteMethod route = routes.get(key);

        if (route == null) {
            route = routes.get("ANY:" + uri);
        }

        if (route == null) {
            return null;
        }

        try {
            Object[] args = prepareArguments(route, session);
            Object result = route.method.invoke(route.controller, args);

            if (result instanceof NanoHTTPD.Response) {
                return (NanoHTTPD.Response) result;
            }

            if (result instanceof JSONObject || result instanceof JSONArray) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json",
                        result.toString());
            }

            String json = gson.toJson(result);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", json);
        } catch (Throwable e) {
            AppLog.e(TAG, "Error invoking method: " + key, e);
            String errorJson = gson.toJson(ApiResponse.error("Internal Server Error: " + e.getMessage()));
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "application/json",
                    errorJson);
        }
    }

    private Object[] prepareArguments(RouteMethod route, IHTTPSession session) throws Exception {
        Class<?>[] parameterTypes = route.parameterTypes;
        Annotation[][] parameterAnnotations = route.parameterAnnotations;
        Object[] args = new Object[parameterTypes.length];

        Map<String, String> params = session.getParms();
        String body = null;

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            Annotation[] annotations = parameterAnnotations[i];

            boolean foundParam = false;
            for (Annotation ann : annotations) {
                if (ann instanceof RequestParam) {
                    RequestParam rp = (RequestParam) ann;
                    String value = params.get(rp.value());
                    if (value == null)
                        value = rp.defaultValue();
                    args[i] = convertType(value, type);
                    foundParam = true;
                    break;
                } else if (ann instanceof RequestBody) {
                    if (body == null) {
                        try {
                            if (session.getHeaders().containsKey("content-length")) {
                                int contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
                                byte[] buffer = new byte[contentLength];
                                int read = 0;
                                while (read < contentLength) {
                                    int r = session.getInputStream().read(buffer, read, contentLength - read);
                                    if (r == -1)
                                        break;
                                    read += r;
                                }
                                body = new String(buffer, 0, read, "UTF-8");
                            } else {
                                // Fallback for no content-length (unlikely for JSON POST but possible)
                                // Use 16k buffer reading
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                byte[] buff = new byte[1024];
                                int r;
                                while ((r = session.getInputStream().available()) > 0
                                        && (r = session.getInputStream().read(buff)) != -1) {
                                    baos.write(buff, 0, r);
                                }
                                body = baos.toString("UTF-8");
                            }
                        } catch (Exception e) {
                            AppLog.e(TAG, "Error reading body", e);
                        }
                    }
                    args[i] = gson.fromJson(body, type);
                    foundParam = true;
                    break;
                }
            }

            if (!foundParam) {
                if (type.equals(IHTTPSession.class)) {
                    args[i] = session;
                } else {
                    args[i] = null;
                }
            }
        }
        return args;
    }

    private Object convertType(String value, Class<?> type) {
        if (value == null)
            return null;
        if (type == String.class)
            return value;
        if (type == Integer.class || type == int.class)
            return Integer.parseInt(value);
        if (type == Long.class || type == long.class)
            return Long.parseLong(value);
        if (type == Boolean.class || type == boolean.class)
            return Boolean.parseBoolean(value);
        return value;
    }

    private static class RouteMethod {
        final Object controller;
        final Method method;
        final Class<?>[] parameterTypes;
        final Annotation[][] parameterAnnotations;

        RouteMethod(Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.parameterTypes = method.getParameterTypes();
            this.parameterAnnotations = method.getParameterAnnotations();
        }
    }
}
