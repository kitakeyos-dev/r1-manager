package com.phicomm.r1manager.mcp.registry;

import android.os.Handler;
import android.os.Looper;
import com.phicomm.r1manager.util.AppLog;
import com.phicomm.r1manager.mcp.model.McpTool;
import com.phicomm.r1manager.mcp.model.McpToolResponse;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executor for async MCP tool execution with callbacks
 */
public class McpToolExecutor {
    private static final String TAG = "McpToolExecutor";

    private final ExecutorService executorService;
    private final Handler mainHandler;

    public McpToolExecutor() {
        this.executorService = Executors.newFixedThreadPool(3); // Max 3 concurrent tool executions
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface ExecutionCallback {
        void onSuccess(McpToolResponse response);

        void onError(String error);
    }

    /**
     * Execute tool asynchronously with callback
     */
    public Future<?> execute(McpTool tool, Map<String, Object> params, ExecutionCallback callback) {
        return executorService.submit(() -> {
            try {
                AppLog.d(TAG, "Executing tool: " + tool.getName());
                McpToolResponse response = tool.execute(params);

                // Post result to main thread
                mainHandler.post(() -> {
                    if (response.isSuccess()) {
                        callback.onSuccess(response);
                    } else {
                        callback.onError(response.getError());
                    }
                });

            } catch (Exception e) {
                AppLog.e(TAG, "Tool execution failed", e);
                mainHandler.post(() -> callback.onError("Execution failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Execute tool synchronously (blocking)
     */
    public McpToolResponse executeSync(McpTool tool, Map<String, Object> params) {
        try {
            return tool.execute(params);
        } catch (Exception e) {
            AppLog.e(TAG, "Sync execution failed", e);
            return McpToolResponse.error("Execution failed: " + e.getMessage());
        }
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executorService.shutdown();
        AppLog.i(TAG, "Executor shutdown");
    }
}
