import os
import sys
import subprocess
import socket
import importlib
import shutil
import math
import requests
from datetime import datetime

# === Plugin Loader ===
PLUGINS_DIR = os.path.join(os.path.dirname(__file__), "plugins")
if os.path.isdir(PLUGINS_DIR):
    sys.path.insert(0, PLUGINS_DIR)
    for fname in os.listdir(PLUGINS_DIR):
        if fname.endswith(".py") and not fname.startswith("_"):
            importlib.import_module(fname[:-3])

# === Command Registry ===
COMMANDS = {}
def command(name, doc=""):
    def decorator(func):
        func.__doc__ = doc or func.__doc__
        COMMANDS[name] = func
        return func
    return decorator

class Terminal:
    def __init__(self):
        self.cwd = os.getcwd()
        self.history = []
    def execute(self, line):
        self.history.append(line)
        parts = line.strip().split()
        if not parts: return ""
        cmd, *args = parts
        fn = COMMANDS.get(cmd.lower())
        if not fn:
            return f"Unknown command: {cmd}. Type 'help'."
        try:
            return fn(self, *args) or ""
        except SystemExit:
            raise
        except Exception as e:
            return f"Error: {e}"
    def help(self):
        lines = ["Available commands:"]
        for name, fn in sorted(COMMANDS.items()):
            doc = fn.__doc__ or ""
            lines.append(f"  {name} {doc}")
        return "\n".join(lines)

# === Built-in Commands ===

@command("help",           "- show this help menu")
def _help(self):             return self.help()
@command("clr",            "- clear screen")
def _clr(self):             return "\n"*100
@command("dt",             "- show date/time")
def _dt(self):              return datetime.now().strftime("%Y-%m-%d %H:%M:%S")
@command("cdir",           "- list directory contents")
def _cdir(self):            return "\n".join(os.listdir(self.cwd))
@command("exit",           "- quit terminal")
def _exit(self):            sys.exit(0)

# Echo & Hello
@command("echo",           "<text> - repeat back your input")
def _echo(self, *words):    return " ".join(words)

# File Operations
@command("read",           "<file> - read file contents")
def _read(self, fn):        return open(fn).read()
@command("write",          "<file> - overwrite file via input()")
def _write(self, fn):
    data = input("Enter text (Ctrl-D to finish):\n")
    with open(fn,"w") as f: f.write(data)
    return f"Wrote to {fn}"
@command("append",         "<file> - append text via input()")
def _append(self, fn):
    data = input("Enter text to append:\n")
    with open(fn,"a") as f: f.write(data)
    return f"Appended to {fn}"
@command("remove",         "<file> - delete file")
def _remove(self, fn):      os.remove(fn); return f"Removed {fn}"
@command("rename",         "<old> <new> - rename file")
def _rename(self, a, b):    os.rename(a,b);  return f"Renamed {a}→{b}"
@command("copy",           "<src> <dest> - copy file")
def _copy(self, a, b):      shutil.copy(a,b); return f"Copied {a}→{b}"
@command("edit",           "<file> - open file in default editor")
def _edit(self, fn):
    if sys.platform=="win32": os.startfile(fn)
    else: subprocess.call(["xdg-open",fn])
    return f"Opening {fn}"

# File Analysis
@command("cknow",          "<file> <char> - count occurrences")
def _cknow(self,fn,ch):
    txt=open(fn).read()
    return f"'{ch}' appears {txt.count(ch)} times"
@command("lknow",          "<file> - count lines")
def _lknow(self,fn):
    return f"{fn} has {len(open(fn).read().splitlines())} lines"
@command("ccount",         "<file> - count non-whitespace chars")
def _ccount(self,fn):
    txt=open(fn).read()
    return f"{fn} has {len([c for c in txt if not c.isspace()])} chars"

# Math Utilities
@command("gcd",            "<a> <b> - greatest common divisor")
def _gcd(self,a,b):         return str(math.gcd(int(a),int(b)))
@command("lcm",            "<a> <b> - least common multiple")
def _lcm(self,a,b):
    a,b=int(a),int(b)
    return str(abs(a*b)//math.gcd(a,b))
@command("tconv",          "<type> <temp> - temp conversions (C↔K/F)")
def _tconv(self,typ,t):
    t=float(t)
    conv = {
        "1":f"{t}°C = {t+273.15}K",
        "2":f"{t}°C = {(t*9/5)+32}°F",
        "3":f"{t}K = {((t-273.15)*9/5)+32}°F",
        "4":f"{t}K = {t-273.15}°C",
        "5":f"{t}°F = {(t-32)*5/9}°C",
        "6":f"{t}°F = {(t-32)*5/9+273.15}K",
    }
    return conv.get(typ,"Invalid tconv type")

# Translation
@command("translate",      "<lang> <text> - translate text")
def _translate(self,*parts):
    from googletrans import Translator, LANGUAGES
    if "to" in parts:
        i=parts.index("to")
        text=" ".join(parts[:i]); lang=parts[i+1]
    else:
        lang, text = parts[0], " ".join(parts[1:])
    code = lang if lang in LANGUAGES else next((c for c,n in LANGUAGES.items() if n.lower()==lang.lower()),None)
    if not code: return f"Unknown language {lang}"
    return Translator().translate(text,dest=code).text

@command("languages",      "- list supported language codes")
def _languages(self):
    from googletrans import LANGUAGES
    return "\n".join(f"{c}:{n}" for c,n in LANGUAGES.items())

# Networking
@command("ping",           "<host> - ping 4 times")
def _ping(self,host="8.8.8.8"):
    return subprocess.check_output(["ping","-c","4",host],text=True)
@command("traceroute",     "<host> - traceroute")
def _tr(self,host="8.8.8.8"):
    return subprocess.check_output(["traceroute",host],text=True)
@command("dns-lookup",     "<host> - DNS lookup")
def _dns(self,host):
    n,a,addrs=socket.gethostbyname_ex(host)
    return f"Name:{n}\nAddrs:{addrs}"
@command("speedtest",      "- internet speed test")
def _speed(self):
    import speedtest
    st = speedtest.Speedtest(); st.get_best_server()
    return f"Download:{st.download()/1e6:.2f}Mbps\nUpload:{st.upload()/1e6:.2f}Mbps"

# Utilities
@command("splitbill",      "<total> <people> [tip%] - split bill")
def _split(self,total,people,tip="0"):
    t,p,tp=float(total),int(people),float(tip)
    each=t*(1+tp/100)/p
    return f"Each pays: {each:.2f}"
@command("weather",        "<location> - current weather")
def _weather(self,*loc):
    place="+".join(loc)
    r=requests.get(f"http://wttr.in/{place}?format=3",timeout=5)
    return r.text

# AI Code Helper (optional)
try:
    import openai
    openai.api_key=os.getenv("OPENAI_API_KEY")
    @command("ai", "<prompt> - ask coding assistant")
    def _ai(self,*p):
        resp = openai.ChatCompletion.create(
            model="gpt-4",
            messages=[
                {"role":"system","content":"You are a coding assistant."},
                {"role":"user",  "content":" ".join(p)}
            ],
            max_tokens=300, temperature=0.2
        )
        return resp.choices[0].message.content.strip()
except ImportError:
    pass

# Entrypoints for Chaquopy
terminal = Terminal()
def execute_command(cmd): return terminal.execute(cmd)
def show_help():       return terminal.help()
