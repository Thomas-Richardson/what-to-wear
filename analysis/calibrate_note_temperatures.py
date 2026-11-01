# %% [markdown]
# # How far below the real temperature is "feels like", in Manchester?
#
# Richie's note "How do I feel at different temperatures" records the REAL air temperature
# plus a yes/no for wind. The What to Wear app works in Open-Meteo's "feels like"
# (apparent) temperature, which also allows for wind and humidity.
#
# To load the note into the app we need to turn "11°C, windy" into a feels-like number.
# Rather than guess, this script takes a year of real hourly weather for Manchester
# (where most of the note was written) and measures the gap between the
# two temperatures on calm hours and on windy hours.
#
# Output: two straight lines, gap = a + b × temperature, one for calm and one for windy.
# Those constants go into the app (model/NoteSeeds.kt).

# %%
import json
import urllib.request
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

# Graphs go next to this script so they can be checked later.
OUT = Path(__file__).resolve().parent
LAT, LON = 53.48, -2.24  # Manchester city centre. City level is plenty for weather.

# %%
# Fetch one full year of hourly weather from Open-Meteo's archive (free, no key).
# A whole year covers every season, because the note runs from January to October.
# We ask for GMT and convert ourselves, because Open-Meteo applies one fixed offset
# to the whole response and so gets local hours wrong after the clocks change.
url = (
    "https://archive-api.open-meteo.com/v1/archive"
    f"?latitude={LAT}&longitude={LON}"
    "&start_date=2025-10-01&end_date=2026-09-30"
    "&hourly=temperature_2m,apparent_temperature,wind_speed_10m,relative_humidity_2m,is_day"
    "&timezone=GMT"
)
with urllib.request.urlopen(url, timeout=60) as response:
    raw = json.load(response)

df = pd.DataFrame(raw["hourly"])
df["time"] = pd.to_datetime(df["time"]).dt.tz_localize("UTC").dt.tz_convert("Europe/London")
df = df.rename(columns={
    "temperature_2m": "temp",
    "apparent_temperature": "feels",
    "wind_speed_10m": "wind",
    "relative_humidity_2m": "humidity",
})
print(f"{len(df):,} hours from {df.time.min():%d %b %Y} to {df.time.max():%d %b %Y}")
df.describe().round(1)

# %%
# Keep only the hours Richie is likely to be out and about (8am–10pm, the same as the
# app's "All day"), and only the temperature range the note covers (about 0–25°C).
# Night-time hours are colder and stiller, so including them would skew the gap.
df["hour"] = df.time.dt.hour
out_and_about = df[(df.hour >= 8) & (df.hour < 22) & df.temp.between(0, 25)].copy()
out_and_about["gap"] = out_and_about.temp - out_and_about.feels
print(f"{len(out_and_about):,} daytime hours kept")

# %%
# What counts as "windy"? The note's Y/N is Richie's own sense of it. A reasonable
# reading: "N" is a typical calm-ish hour (below the median wind speed) and "Y" is a
# noticeably windy hour (above the 75th percentile). Print the cut-offs so they can be checked.
median_wind = out_and_about.wind.median()
windy_cutoff = out_and_about.wind.quantile(0.75)
print(f"Median daytime wind: {median_wind:.1f} km/h. Windy (top quarter) from: {windy_cutoff:.1f} km/h")

out_and_about["group"] = np.select(
    [out_and_about.wind < median_wind, out_and_about.wind >= windy_cutoff],
    ["calm (N)", "windy (Y)"],
    default="in between",
)
out_and_about.groupby("group").gap.describe().round(2)

# %%
# GRAPH 1: the gap against wind speed. If wind matters, the gap should climb as wind rises.
fig, ax = plt.subplots(figsize=(9, 5))
ax.scatter(out_and_about.wind, out_and_about.gap, s=3, alpha=0.15, color="#3A7BD5", label="one hour")
bins = np.arange(0, out_and_about.wind.max() + 5, 5)
medians = out_and_about.groupby(pd.cut(out_and_about.wind, bins), observed=True).gap.median()
ax.plot([b.mid for b in medians.index], medians.values, color="#D64545", linewidth=2.5, marker="o",
        label="median in each 5 km/h band")
ax.axvline(median_wind, color="grey", linestyle="--", linewidth=1)
ax.axvline(windy_cutoff, color="grey", linestyle=":", linewidth=1)
ax.text(median_wind, ax.get_ylim()[1] * 0.95, " calm below", color="grey", fontsize=9)
ax.text(windy_cutoff, ax.get_ylim()[1] * 0.85, " windy above", color="grey", fontsize=9)
ax.set_xlabel("Wind speed (km/h)")
ax.set_ylabel("Real temperature minus feels-like (°C)")
ax.set_title("Manchester, daytime hours, Oct 2025 – Sep 2026: wind makes it feel colder")
ax.legend(loc="lower right")
ax.grid(alpha=0.3)
fig.tight_layout()
fig.savefig(OUT / "gap_vs_wind.png", dpi=130)
plt.show()

# %%
# GRAPH 2: the gap against temperature, split by calm and windy. Humidity makes warm
# days feel closer to (or above) the real temperature, so the gap probably shrinks as
# it gets warmer. If the lines are roughly straight, a straight-line fit per group is fine.
fig, ax = plt.subplots(figsize=(9, 5))
colours = {"calm (N)": "#3A7BD5", "windy (Y)": "#D64545"}
fits = {}
for group, colour in colours.items():
    g = out_and_about[out_and_about.group == group]
    ax.scatter(g.temp, g.gap, s=3, alpha=0.12, color=colour)
    # Median in each 1°C band, so a few odd hours can't drag the line around.
    band_medians = g.groupby(g.temp.round()).gap.median()
    ax.plot(band_medians.index, band_medians.values, color=colour, linewidth=2.5, marker="o", markersize=4,
            label=f"{group}: median in each 1°C band")
    # Straight line through the band medians: gap = a + b × temperature.
    b, a = np.polyfit(band_medians.index, band_medians.values, 1)
    fits[group] = (a, b)
    xs = np.array([0, 25])
    ax.plot(xs, a + b * xs, color=colour, linestyle="--", linewidth=1.5,
            label=f"{group} fit: gap = {a:.2f} {b:+.3f} × temp")
ax.set_xlabel("Real temperature (°C)")
ax.set_ylabel("Real temperature minus feels-like (°C)")
ax.set_title("How much colder it feels, by temperature: calm vs windy hours")
ax.legend(loc="upper right", fontsize=9)
ax.grid(alpha=0.3)
fig.tight_layout()
fig.savefig(OUT / "gap_vs_temperature.png", dpi=130)
plt.show()

# %%
# TABLE: what the fits say for the temperatures in Richie's note. These are the
# feels-like values the app will use for each row.
table = pd.DataFrame({"real temp (°C)": range(0, 26)})
for group, (a, b) in fits.items():
    table[f"feels like, {group}"] = (table["real temp (°C)"] - (a + b * table["real temp (°C)"])).round(1)
print(table.to_string(index=False))
for group, (a, b) in fits.items():
    print(f"{group}: gap = {a:.3f} + ({b:.4f}) × temp")
