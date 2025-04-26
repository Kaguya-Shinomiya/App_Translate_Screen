import os
import shutil
from argostranslate import package

# 1. Lấy danh sách package có thể tải
available_packages = package.get_available_packages()

# 2. Chọn đúng package từ ja → en
ja_en_pkg = next(
    p for p in available_packages
    if p.from_code == "ja" and p.to_code == "en"
)

# 3. Tải file về thư mục tạm
temp_path = ja_en_pkg.download()

# 4. Chuyển file sang thư mục ổ D
target_dir = "D:/argos_models"
os.makedirs(target_dir, exist_ok=True)

# Tên file giống tên gốc
target_path = os.path.join(target_dir, os.path.basename(temp_path))
shutil.copy(temp_path, target_path)

# 5. Cài đặt từ file đã lưu
package.install_from_path(target_path)

print("Đã cài đặt thành công từ:", target_path)
