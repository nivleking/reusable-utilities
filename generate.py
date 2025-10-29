import json, base64
with open('response.json','r') as f:
    resp = json.load(f)
b64 = resp['output_schema']['data']
pdf = base64.b64decode(b64)
with open('out.pdf','wb') as f:
    f.write(pdf)