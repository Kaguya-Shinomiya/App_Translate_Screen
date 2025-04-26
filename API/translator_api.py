# from fastapi import FastAPI
# from pydantic import BaseModel
# from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
# import torch
# import uvicorn

# import base64
# import io
# from PIL import Image
# import pytesseract
# from collections import defaultdict

# # Đường dẫn tới Tesseract nếu dùng Windows
# pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

# app = FastAPI()

# # Model & tokenizer
# model = None
# tokenizer = None
# device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# class OCRRequest(BaseModel):
#     image_base64: str

# @app.on_event("startup")
# def load_model():
#     global model, tokenizer
#     model_name = "facebook/nllb-200-distilled-600M"  # ✅ Model dịch tốt hơn cho tiếng Nhật hội thoại
#     tokenizer = AutoTokenizer.from_pretrained(model_name)
#     model = AutoModelForSeq2SeqLM.from_pretrained(model_name).to(device)

# @app.post("/ocr_translate")
# def ocr_translate(req: OCRRequest):
#     image_data = base64.b64decode(req.image_base64)
#     image = Image.open(io.BytesIO(image_data))

#     # OCR với pytesseract, trả về dict từng dòng/từ
#     ocr_data = pytesseract.image_to_data(image, lang="jpn", output_type=pytesseract.Output.DICT)

#     lines = defaultdict(list)

#     # Gom text theo dòng
#     for i in range(len(ocr_data["text"])):
#         text = ocr_data["text"][i].strip()
#         if not text:
#             continue

#         key = (ocr_data["block_num"][i], ocr_data["par_num"][i], ocr_data["line_num"][i])
#         lines[key].append({
#             "text": text,
#             "x": ocr_data["left"][i],
#             "y": ocr_data["top"][i],
#             "w": ocr_data["width"][i],
#             "h": ocr_data["height"][i]
#         })

#     results = []

#     for key, words in lines.items():
#         # ✅ Ghép từ tiếng Nhật sát nhau (không có khoảng trắng)
#         full_text = "".join(w["text"] for w in words)

#         # ✅ Tiền xử lý (nếu cần thêm hiệu quả)
#         cleaned_text = full_text.replace("！", "!").replace("？", "?").replace("、", ",").replace("。", ".")

#         # ✅ Dịch
#         inputs = tokenizer(cleaned_text, return_tensors="pt", padding=True).to(device)
#         output = model.generate(**inputs, max_length=512)
#         translated_text = tokenizer.decode(output[0], skip_special_tokens=True)

#         # Bounding box
#         min_x = min(w["x"] for w in words)
#         min_y = min(w["y"] for w in words)
#         max_x = max(w["x"] + w["w"] for w in words)
#         max_y = max(w["y"] + w["h"] for w in words)

#         results.append({
#             "original": full_text,
#             "translated": translated_text,
#             "x": min_x,
#             "y": min_y,
#             "width": max_x - min_x,
#             "height": max_y - min_y
#         })

#     return results

# @app.get("/")
# def root():
#     return {"message": "Translation server is up."}

# if __name__ == "__main__":
#     load_model()
#     uvicorn.run("translator_api:app", host="0.0.0.0", port=5000, workers=1)
