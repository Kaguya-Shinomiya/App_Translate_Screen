import subprocess

def mistral_translate(text):
    prompt = f"Translate the following Japanese into English, preserving nuance and character emotion:\n\n{text}\n\nEnglish:"
    
    # Chạy mô hình "mistral" bằng ollama
    result = subprocess.run(
        ['ollama', 'run', 'mistral'],
        input=prompt,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    # Xử lý lỗi (nếu có)
    if result.stderr:
        print("Lỗi:", result.stderr)

    output = result.stdout.strip()
    return output

# Ví dụ dịch
japanese_text = "誰がうるさいって！？わ、私は早瀬ユウカ！覚えておいてください、先生！"
english_translation = mistral_translate(japanese_text)

print("Japanese:", japanese_text)
print("Translated:", english_translation)
