from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from argostranslate import translate
import torch
import uvicorn
import base64
import io
from PIL import Image
import pytesseract
from collections import defaultdict

# Đường dẫn tới Tesseract nếu dùng Windows
pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

app = FastAPI()

# Biến toàn cục lưu model dịch
ja_lang = None
en_lang = None

# Class yêu cầu OCR
class OCRRequest(BaseModel):
    image_base64: str

# Khởi tạo mô hình dịch khi ứng dụng khởi động
@app.on_event("startup")
def load_model():
    global ja_lang, en_lang 
    installed_languages = translate.get_installed_languages()
    ja_lang = next(l for l in installed_languages if l.code == "ja")
    en_lang = next(l for l in installed_languages if l.code == "en")

# Endpoint nhận yêu cầu OCR và dịch
@app.post("/ocr_translate")
def ocr_translate(req: OCRRequest):
    try:
        # Giải mã base64 và mở ảnh
        image_data = base64.b64decode(req.image_base64)
        image = Image.open(io.BytesIO(image_data))

        # OCR với pytesseract, trả về dict từng dòng/từ
        ocr_data = pytesseract.image_to_data(image, lang="jpn", output_type=pytesseract.Output.DICT)

        lines = defaultdict(list)

        # Gom text theo dòng
        for i in range(len(ocr_data["text"])):
            text = ocr_data["text"][i].strip()
            if not text:
                continue

            key = (ocr_data["block_num"][i], ocr_data["par_num"][i], ocr_data["line_num"][i])
            lines[key].append({
                "text": text,
                "x": ocr_data["left"][i],
                "y": ocr_data["top"][i],
                "w": ocr_data["width"][i],
                "h": ocr_data["height"][i]
            })

        # Tạo bản dịch và bounding box cho mỗi dòng
        results = []
        translation = ja_lang.get_translation(en_lang)  # Chỉ gọi một lần
        for key, words in lines.items():
            full_text = "".join(w["text"] for w in words)

            # Dịch văn bản
            translated_text = translation.translate(full_text)

            # Tính toán bounding box cho văn bản
            min_x = min(w["x"] for w in words)
            min_y = min(w["y"] for w in words)
            max_x = max(w["x"] + w["w"] for w in words)
            max_y = max(w["y"] + w["h"] for w in words)

            # Thêm kết quả vào danh sách
            results.append({
                "original": full_text,
                "translated": translated_text,
                "x": min_x,
                "y": min_y,
                "width": max_x - min_x,
                "height": max_y - min_y
            })

        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error processing image: {str(e)}")

# Endpoint gốc
@app.get("/")
def root():
    return {"message": "Translation server is up."}

# Chạy ứng dụng
if __name__ == "__main__":
    load_model()
    uvicorn.run("translate:app", host="0.0.0.0", port=5000, workers=1)
