from argostranslate import translate

# Lấy danh sách ngôn ngữ đã cài
installed_languages = translate.get_installed_languages()

# Lấy đối tượng ngôn ngữ Nhật và Anh
ja_lang = next(l for l in installed_languages if l.code == "ja")
en_lang = next(l for l in installed_languages if l.code == "en")

# Dịch văn bản
text = "誰がうるさいって！？わ、私は早瀬ユウカ！覚えておいてください、先生！"
text = "誰がうるさいって!?わ、私は早ユウカ!覚えておいてください、先生！"
translated_text = ja_lang.get_translation(en_lang).translate(text)
print("Bản dịch:", translated_text)
