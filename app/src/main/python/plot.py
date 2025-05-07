import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import io

def plot(x_str, y_str):
    # Split on commas, strip out any empty strings
    xs = [float(v) for v in x_str.split(",") if v.strip()]
    ys = [float(v) for v in y_str.split(",") if v.strip()]
    # Now xs and ys are lists of floats
    plt.figure()
    plt.plot(xs, ys)
    plt.xlabel("X")
    plt.ylabel("Y")
    plt.tight_layout()

    buf = io.BytesIO()
    plt.savefig(buf, format="png")
    buf.seek(0)
    return buf.getvalue()
