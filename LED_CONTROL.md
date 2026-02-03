# Hướng dẫn Điều khiển LED Phicomm R1

Tài liệu này hướng dẫn cách sử dụng lệnh hệ thống `lights_test` để điều khiển hai cụm đèn LED trên loa Phicomm R1: **Đèn bên trong (Internal LED)** và **Đèn viền (Ring LED)**.

## Cấu trúc lệnh cơ bản
Lệnh được thực thi thông qua shell của hệ thống:
```bash
lights_test set <mask> <color_or_brightness>
```
- `<mask>`: Mã Hex đại diện cho các bóng LED muốn bật (dạng bitmask).
- `<color_or_brightness>`: Mã màu RGB (cho đèn trong) hoặc độ sáng (cho đèn viền), tất cả đều ở dạng Hex.

---

## 1. Đèn bên trong (Internal LED)
Đèn này nằm phía trong, có thể hiển thị màu sắc RGB.

- **Số lượng LED**: 15 bóng.
- **Định dạng Mask**: 15-bit Hex.
- **Định dạng Màu**: RGB Hex (6 ký tự).

### Ví dụ:
| Mục tiêu | Lệnh | Giải thích |
| :--- | :--- | :--- |
| **Bật tất cả 15 LED màu Đỏ** | `lights_test set 7fff ff0000` | `7fff` = 111 1111 1111 1111 (15 bit 1) |
| **Bật tất cả 15 LED màu Xanh lá** | `lights_test set 7fff 00ff00` | |
| **Bật tất cả 15 LED màu Xanh dương** | `lights_test set 7fff 0000ff` | |
| **Chỉ bật LED số 1 màu Trắng** | `lights_test set 1 ffffff` | `1` = bit đầu tiên |
| **Chỉ bật LED số 15 màu Trắng** | `lights_test set 4000 ffffff` | `4000` = bit thứ 15 |
| **Tắt toàn bộ** | `lights_test set 0 0` | |

---

## 2. Đèn viền (Ring LED)
Vòng viền ngoài loa, chỉ hiển thị màu trắng với cường độ sáng khác nhau.

- **Số lượng LED**: 24 bóng.
- **Định dạng Mask**: 40-bit Hex (10 ký tự Hex).
  - **Cấu trúc**: [24 bit LED] [15 bit trống = 0].
  - Để bật đèn viền, bạn cần lấy mã bitmask của 24 LED và **dịch trái 15 bit**.
- **Định dạng Độ sáng**: 00 - FF (0 đến 255).

### Ví dụ:
| Mục tiêu | Lệnh | Giải thích |
| :--- | :--- | :--- |
| **Bật tất cả 24 LED (Sáng nhất)** | `lights_test set 7FFFFF8000 ff` | `7FFFFF8` = 23 bit 1, thêm `000` (dịch 15 bit) |
| **Bật tất cả 24 LED (Sáng mờ)** | `lights_test set 7FFFFF8000 33` | |
| **Chỉ bật LED viền số 1** | `lights_test set 8000 ff` | Bit thứ 16 (1 dịch trái 15) |
| **Chỉ bật LED viền số 24** | `lights_test set 4000000000 ff` | Bit thứ 39 (1 dịch trái 38) |

---

## Cách tính Mask 40-bit cho Ring LED (Javascript)
Nếu bạn muốn tính toán Mask bằng code:
```javascript
function getRingMask(ledIndices) {
    let mask = BigInt(0);
    ledIndices.forEach(index => {
        // index từ 0 đến 23
        mask |= (BigInt(1) << BigInt(index + 15));
    });
    return mask.toString(16).toUpperCase();
}
```

## Lưu ý quan trọng
1. Lệnh `lights_test` yêu cầu quyền **system** hoặc **root**. Trong ứng dụng R1 Manager, lệnh này được gửi qua `HardwareClient` tới dịch vụ phần cứng chạy ngầm để thực thi.
2. Việc sử dụng Mask sai (ví dụ không dịch 15 bit cho Ring LED) có thể khiến lệnh không có tác dụng hoặc điều khiển nhầm cụm LED.
