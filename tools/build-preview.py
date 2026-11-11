from pathlib import Path
root=Path(__file__).resolve().parent.parent
p=root/'app/src/main/assets/ui'
s=(p/'index.html').read_text(encoding='utf-8')
s=s.replace('<link rel="stylesheet" href="style.css">','<style>'+(p/'style.css').read_text(encoding='utf-8')+'</style>')
for f in ['license.js','demo.js','app.js']:
    s=s.replace('<script src="'+f+'"></script>','<script>'+(p/f).read_text(encoding='utf-8')+'</script>')
(root/'docs'/'LiderTv_Onizleme.html').write_text(s,encoding='utf-8')
(root.parent/'LiderTv_0.1.0_Onizleme.html').write_text(s,encoding='utf-8')
print('Preview generated')
