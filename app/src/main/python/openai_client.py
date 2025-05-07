# app/src/main/python/openai_client.py
import os
import json
import requests

api_key = None

def set_api_key(key):
    global api_key
    api_key = key

def chat(prompt):
    if not api_key:
        raise ValueError("API key not set")
    url = "https://api.openai.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    body = {
        "model": "gpt-3.5-turbo",
        "messages": [{"role": "user", "content": prompt}],
    }
    r = requests.post(url, headers=headers, data=json.dumps(body))
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]
