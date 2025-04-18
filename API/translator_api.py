from fastapi import FastAPI, Request
from pydantic import BaseModel
from transformers import MarianMTModel, MarianTokenizer
import torch
import uvicorn

app = FastAPI()

# Khai báo biến toàn cục
model = None
tokenizer = None
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

class TranslationRequest(BaseModel):
    text: str

@app.on_event("startup")
def load_model():
    global model, tokenizer
    model_name = "Helsinki-NLP/opus-mt-mul-en"
    tokenizer = MarianTokenizer.from_pretrained(model_name)
    model = MarianMTModel.from_pretrained(model_name).to(device)

@app.post("/translate")
async def translate(req: TranslationRequest):
    inputs = tokenizer(req.text, return_tensors="pt", truncation=True).to(device)
    print(req.text)
    output = model.generate(**inputs, max_length=256)
    result = tokenizer.decode(output[0], skip_special_tokens=True)
    return {"translated": result}

@app.get("/")
def root():
    return {"message": "Translation server is up."}




if __name__ == "__main__":
    load_model()
    uvicorn.run("translator_api:app", host="0.0.0.0", port=5000, workers=1)
