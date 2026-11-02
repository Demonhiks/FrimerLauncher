"""
Frimer: standalone Minecraft launcher (Vanilla / Fabric / Quilt / Forge / NeoForge).
Серверная часть вшита прямо в этот файл.
"""
import base64
import hashlib
import io
import json
import math
import os
import platform
import random
import re
import shutil
import subprocess
import sys
import tarfile
import threading
import time
import uuid
import zipfile
from collections import deque
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from html import escape as _esc
from pathlib import Path
from types import SimpleNamespace

import requests
try:
    import ai_fixer
except ImportError:
    ai_fixer = None
try:
    import tomllib
except ImportError:
    tomllib = None
try:
    from pypresence import Presence
except ImportError:
    Presence = None
try:
    import psutil
except ImportError:
    psutil = None
from PyQt6.QtCore import (
    QPoint, QPointF, QPropertyAnimation, QRectF, QSettings, QSize, Qt, QThread, QTimer, QUrl, pyqtSignal,
)
from PyQt6.QtGui import (
    QBrush, QColor, QDesktopServices, QFont, QFontMetrics, QFontMetricsF, QIcon, QImage, QImageReader,
    QLinearGradient, QPainter, QPainterPath, QPen, QPixmap, QPolygonF, QRadialGradient,
    QTextCharFormat, QTextCursor,
)
from PyQt6.QtWidgets import (
    QAbstractItemView, QApplication, QCheckBox, QComboBox, QDialog, QDialogButtonBox,
    QFileDialog, QFormLayout, QFrame, QGraphicsDropShadowEffect, QHBoxLayout, QInputDialog, QLabel,
    QLineEdit, QListView, QListWidget, QListWidgetItem, QMenu, QMessageBox,
    QPlainTextEdit, QProgressBar, QPushButton, QSlider, QSplitter, QStackedWidget,
    QTabWidget, QTextBrowser, QToolTip, QTreeWidget, QTreeWidgetItem, QVBoxLayout, QWidget,
)
from PyQt6 import sip

MUTED_TEXT = "#8a93a3"
GAME_DIR = Path.home() / ".mylauncher"
INSTANCES_DIR = GAME_DIR / "instances"
LOGS_DIR = GAME_DIR / "logs"
RUNTIME_DIR = GAME_DIR / "runtime"
VERSIONS_DIR = GAME_DIR / "versions"
LIBRARIES_DIR = GAME_DIR / "libraries"
ASSETS_DIR = GAME_DIR / "assets"
SERVERS_DIR = GAME_DIR / "servers"
SKINS_DIR = GAME_DIR / "skins"
LOADERS = ["Vanilla", "Fabric", "Quilt", "Forge", "NeoForge"]

APP_VERSION = "2.1.0"
UPDATE_MANIFEST_URL = ""
CHANGELOG = {
    "2.1.0": [
        "Мрpack теперь нормально ловится при перетаскивании в список инстансов",
        "Галочки включения/выключения модов - тёмный квадратик с галочкой",
        "playit.gg подхватывается/скачивается сам, ссылка для привязки видна в консоли сервера",
        "Новый главный экран: карточка выбранной сборки и обновлённые плитки",
    ],
}

MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES_URL = "https://resources.download.minecraft.net"
ADOPTIUM_API = "https://api.adoptium.net/v3"
MOJANG_PROFILE_API = "https://api.mojang.com/users/profiles/minecraft"
MOJANG_SESSION_API = "https://sessionserver.mojang.com/session/minecraft/profile"

PAPER_API = "https://fill.papermc.io/v3/projects/paper"
PAPER_UA = {"User-Agent": "Frimer-Launcher/2.0 (contact: admin@example.com)"}

FABRIC_META = "https://meta.fabricmc.net/v2"
FORGE_PROMOS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"
NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"

CORE_NAMES = {"paper": "Paper", "vanilla": "Vanilla", "fabric": "Fabric",
              "forge": "Forge", "neoforge": "NeoForge"}
MODDED_CORES = ("fabric", "forge", "neoforge")
CORE_LOADER = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}
CORE_HINTS = {
    "paper": "Быстрое ядро с плагинами (Bukkit/Spigot). Модов нет.",
    "vanilla": "Чистый сервер от Mojang: без плагинов и модов.",
    "fabric": "Настоящие моды Fabric (папка mods). Ставится быстро, при первом запуске "
              "докачает файлы Minecraft. Игрокам нужен клиент Fabric с теми же модами.",
    "forge": "Настоящие моды Forge (папка mods). Установка занимает несколько минут. "
             "Игрокам нужен клиент Forge с теми же модами.",
    "neoforge": "Настоящие моды NeoForge (папка mods). Установка занимает несколько минут. "
                "Игрокам нужен клиент NeoForge с теми же модами.",
}
FORGE_SERVER_MIN_MC = (1, 12, 2)
PLAYIT_PATHS = [
    Path(os.environ.get("LOCALAPPDATA", "")) / "playit_gg" / "bin" / "playit.exe",
    Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "playit_gg" / "playit.exe",
    Path(os.environ.get("ProgramFiles(x86)", "C:/Program Files (x86)")) / "playit_gg" / "playit.exe",
    Path("C:/Program Files/playit_gg/bin/playit.exe"),
    Path("C:/Program Files (x86)/playit_gg/bin/playit.exe"),
]
PLAYIT_DOWNLOAD_URL = "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86-signed.exe"

LOADER_COLORS = {
    "Vanilla":  ("#3a4150", "#dfe3ea"),
    "Paper":    ("#4db6ac", "#08302c"),
    "Fabric":   ("#dbb27e", "#3a2a10"),
    "Quilt":    ("#c17fd6", "#2e1038"),
    "Forge":    ("#e08a3c", "#3a1f05"),
    "NeoForge": ("#4fa8e0", "#0a2233"),
}

ICON_PATH = Path(__file__).parent / "icon.ico"
UA = {"User-Agent": "Frimer-Launcher/2.0"}

DISCORD_CLIENT_ID = ""
DISCORD_LARGE_IMAGE = ""

THEMES = {
    "dark": {
        "bg": "#15181e", "sidebar": "#1d2129", "card": "#232833",
        "card_sel": "#3a3222", "border": "#2c323d", "text": "#dfe3ea",
        "muted": "#8a93a3", "accent": "#f2a93b", "accent_dark": "#b8791a",
        "accent_light": "#ffbd52", "input": "#15181e", "menu": "#1d2129",
        "err": "#ff6b6b", "warn": "#ffcc66", "ok": "#7ad47a",
        "selection": "#3a3222", "selection_text": "#ffbd52",
        "tooltip_bg": "#232833", "tooltip_text": "#dfe3ea", "tooltip_border": "#f2a93b",
        "hover": "#2a3040", "disabled_bg": "#1a1d24", "disabled_text": "#5a6170",
        "shadow": "rgba(0, 0, 0, 90)",
    },
    "light": {
        "bg": "#f4f5f7", "sidebar": "#ffffff", "card": "#ffffff",
        "card_sel": "#fff3d9", "border": "#d8dce4", "text": "#232833",
        "muted": "#6d7686", "accent": "#e08a1c", "accent_dark": "#a56410",
        "accent_light": "#ffb84d", "input": "#ffffff", "menu": "#ffffff",
        "err": "#c0392b", "warn": "#a56b00", "ok": "#2e7d32",
        "selection": "#fff3d9", "selection_text": "#a56410",
        "tooltip_bg": "#ffffff", "tooltip_text": "#232833", "tooltip_border": "#e08a1c",
        "hover": "#f0f2f6", "disabled_bg": "#eceef2", "disabled_text": "#9aa1ad",
        "shadow": "rgba(0, 0, 0, 30)",
    },
}


def _rgba(hex_color: str, alpha: int) -> str:
    h = hex_color.lstrip("#")
    return f"rgba({int(h[0:2], 16)}, {int(h[2:4], 16)}, {int(h[4:6], 16)}, {alpha})"


def build_style(theme: str = "dark", fancy: bool = False) -> str:
    t = THEMES.get(theme, THEMES["dark"])
    side_bg = _rgba(t["sidebar"], 205) if fancy else t["sidebar"]
    check_fill = "#090b0e" if theme == "dark" else "#232833"
    check_icon = checkmark_icon_path(t["accent"])
    return f"""
QWidget#root {{ background: {t['bg']}; }}
* {{ font-family: 'Segoe UI'; font-size: 14px; color: {t['text']}; }}
QFrame#sidebar {{ background: {side_bg}; border-right: 1px solid {t['border']}; }}
QLabel#title {{ font-family: 'Bahnschrift', 'Segoe UI'; font-size: 26px; font-weight: 600; color: {t['accent']}; }}
QLabel#subtitle {{ color: {t['muted']}; }}
QLabel#field {{ color: {t['muted']}; font-size: 12px; }}
QLabel#value {{ color: {t['accent']}; font-size: 12px; font-weight: 600; }}
QLabel#status {{ font-family: 'Bahnschrift', 'Segoe UI'; font-size: 18px; font-weight: 600; }}
QLineEdit, QComboBox {{ background: {t['input']}; border: 1px solid {t['border']}; border-radius: 4px; padding: 8px 10px; }}
QLineEdit:focus, QComboBox:focus {{ border: 1px solid {t['accent']}; }}
QLineEdit:disabled, QComboBox:disabled {{ background: {t['disabled_bg']}; color: {t['disabled_text']}; }}
QComboBox QAbstractItemView {{ background: {t['menu']}; border: 1px solid {t['border']}; outline: 0;
    selection-background-color: {t['card_sel']}; selection-color: {t['accent']}; }}
QPushButton {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px; padding: 6px 16px; }}
QPushButton:hover {{ border-color: {t['accent']}; }}
QPushButton:disabled {{ color: {t['disabled_text']}; border-color: {t['border']}; background: {t['disabled_bg']}; }}
QPushButton#small {{ background: {t['bg']}; border: 1px solid {t['border']}; color: {t['muted']}; padding: 6px 0; font-size: 13px; }}
QPushButton#small:hover {{ color: {t['text']}; border-color: {t['accent']}; }}
QPushButton#mini {{ background: transparent; border: 1px solid {t['border']}; border-radius: 4px; color: {t['muted']}; padding: 4px 10px; font-size: 13px; }}
QPushButton#mini:hover {{ color: {t['text']}; border-color: {t['accent']}; }}
QPushButton#mini:checked {{ color: {t['accent']}; border-color: {t['accent']}; background: {t['card_sel']}; }}
QPushButton#small:checked {{ background: {t['card_sel']}; border-color: {t['accent']}; color: {t['accent']}; font-weight: 600; }}
QPushButton#nick {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px;
    color: {t['text']}; padding: 5px 10px; font-size: 14px; font-weight: 600; text-align: left; }}
QPushButton#nick:hover {{ border-color: {t['accent']}; color: {t['accent']}; }}
QPushButton#gear {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px;
    color: {t['muted']}; font-size: 14px; padding: 0; }}
QPushButton#gear:hover {{ color: {t['accent']}; border-color: {t['accent']}; }}
QPushButton#folder {{ background: transparent; border: 1px solid {t['border']}; padding: 7px 12px; color: {t['muted']}; font-size: 13px; }}
QPushButton#folder:hover {{ color: {t['text']}; border-color: {t['accent']}; }}
QPushButton#danger {{ background: transparent; border: 1px solid {t['err']}; color: {t['err']}; padding: 6px 12px; font-size: 13px; }}
QPushButton#danger:hover {{ background: {t['err']}; color: #fff; }}
QPushButton#play {{ font-family: 'Bahnschrift', 'Segoe UI'; font-size: 20px; font-weight: 600;
    background: {t['accent']}; color: #241703; border: none; border-bottom: 4px solid {t['accent_dark']};
    border-radius: 4px; padding: 12px; }}
QPushButton#play:hover {{ background: {t['accent_light']}; }}
QPushButton#play:pressed {{ border-bottom: 1px solid {t['accent_dark']}; padding-top: 15px; }}
QPushButton#play:disabled {{ background: {t['border']}; color: {t['muted']}; border-bottom: 4px solid {t['border']}; }}
QPlainTextEdit {{ background: {t['bg']}; border: 1px solid {t['border']}; border-radius: 4px; padding: 8px;
    font-family: 'Consolas', monospace; font-size: 12px; color: {t['text']};
    selection-background-color: {t['selection']}; selection-color: {t['selection_text']}; }}
QTextBrowser {{ background: {t['bg']}; border: 1px solid {t['border']}; border-radius: 4px; padding: 8px;
    color: {t['text']}; selection-background-color: {t['selection']}; selection-color: {t['selection_text']}; }}
QProgressBar {{ background: {t['border']}; border: none; min-height: 6px; max-height: 6px; border-radius: 3px; }}
QProgressBar::chunk {{ background: {t['accent']}; border-radius: 3px; }}
QSlider::groove:horizontal {{ height: 4px; background: {t['border']}; }}
QSlider::sub-page:horizontal {{ background: {t['accent']}; }}
QSlider::handle:horizontal {{ background: {t['text']}; width: 14px; height: 14px; margin: -5px 0; border-radius: 2px; }}
QSlider::handle:horizontal:hover {{ background: {t['accent_light']}; }}
QScrollBar:vertical {{ background: transparent; width: 10px; margin: 0; }}
QScrollBar::handle:vertical {{ background: {t['border']}; min-height: 30px; border-radius: 4px; }}
QScrollBar::handle:vertical:hover {{ background: {t['muted']}; }}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0; }}
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{ background: transparent; }}
QScrollBar:horizontal {{ background: transparent; height: 10px; margin: 0; }}
QScrollBar::handle:horizontal {{ background: {t['border']}; min-width: 30px; border-radius: 4px; }}
QScrollBar::handle:horizontal:hover {{ background: {t['muted']}; }}
QScrollBar::add-line:horizontal, QScrollBar::sub-line:horizontal {{ width: 0; }}
QScrollBar::add-page:horizontal, QScrollBar::sub-page:horizontal {{ background: transparent; }}
QMessageBox, QDialog {{ background: {t['sidebar']}; }}
QMenu {{ background: {t['menu']}; border: 1px solid {t['border']}; padding: 4px; }}
QMenu::item {{ padding: 6px 26px; margin: 2px 3px; border: 1px solid {t['border']}; border-radius: 4px; }}
QMenu::item:selected {{ background: {t['card_sel']}; color: {t['accent']}; border: 1px solid {t['accent']}; }}
QMenu::item:disabled {{ color: {t['disabled_text']}; border-color: transparent; }}
QMenu::separator {{ height: 1px; background: {t['border']}; margin: 4px 6px; }}
QToolTip {{ background: {t['tooltip_bg']}; color: {t['tooltip_text']};
    border: 1px solid {t['tooltip_border']}; padding: 6px 8px; border-radius: 4px; font-size: 12px; }}
QCheckBox {{ spacing: 8px; }}
QCheckBox::indicator {{ width: 16px; height: 16px; border: 1px solid {t['border']}; border-radius: 3px;
    background: {check_fill}; }}
QCheckBox::indicator:hover {{ border-color: {t['accent']}; }}
QCheckBox::indicator:checked {{ background: {check_fill}; border-color: {t['accent']}; image: url({check_icon}); }}
QCheckBox::indicator:disabled {{ border-color: {t['border']}; background: {t['disabled_bg']}; }}
QGroupBox {{ border: 1px solid {t['border']}; border-radius: 4px; margin-top: 12px; padding-top: 8px; }}
QGroupBox::title {{ subcontrol-origin: margin; left: 10px; padding: 0 4px; color: {t['muted']}; }}
QSplitter::handle {{ background: {t['border']}; }}
QSplitter::handle:horizontal {{ width: 1px; }}
QSplitter::handle:vertical {{ height: 1px; }}
QDialog#modbrowser {{ background: {t['bg']}; }}
QListWidget#modlist {{ background: transparent; border: none; outline: 0; }}
QListWidget#modlist::item {{ background: transparent; }}
QFrame#modrow {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px; }}
QLabel#modicon {{ background: {t['card']}; border-radius: 4px; }}
QLabel#modtitle {{ font-family: 'Bahnschrift', 'Segoe UI'; font-size: 16px; font-weight: 600; }}
QLabel#modmeta {{ color: {t['muted']}; font-size: 12px; }}
QLabel#moddesc {{ color: {t['text']}; font-size: 13px; }}
QPushButton#install {{ background: transparent; border: 1px solid {t['accent']}; color: {t['accent']}; font-weight: 600; padding: 8px 0; }}
QPushButton#install:hover {{ background: {t['card_sel']}; }}
QPushButton#install:disabled {{ border-color: {t['border']}; color: {t['muted']}; }}
QPushButton#installed {{ background: transparent; border: 1px solid {t['border']}; color: {t['muted']}; font-weight: 600; padding: 8px 0; }}
QLabel#counter {{ color: {t['muted']}; font-size: 12px; }}
QListWidget#instances {{ background: transparent; border: 1px dashed transparent; outline: 0; }}
QListWidget#instances[dragging="true"] {{ border: 1px dashed {t['accent']}; background: {t['card_sel']}; border-radius: 6px; }}
QListWidget#instances::item {{ background: transparent; padding: 0; }}
QListWidget#instances::item:selected {{ background: transparent; }}
QListWidget#instances::item:hover {{ background: transparent; }}
QFrame#instance_card {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 6px; }}
QFrame#instance_card[selected="true"] {{ background: {t['card_sel']}; border: 1px solid {t['accent']}; }}
QLabel#inst_name {{ font-family: 'Bahnschrift', 'Segoe UI'; font-size: 14px; font-weight: 600; }}
QLabel#inst_sub {{ color: {t['muted']}; font-size: 11px; }}
QFrame#tile {{ background: transparent; border: 1px solid transparent; border-radius: 8px; }}
QFrame#tile:hover {{ background: {t['hover']}; }}
QFrame#tile[selected="true"] {{ background: {t['card_sel']}; border: 1px solid {t['accent']}; }}
QLabel#tile_name {{ font-size: 12px; }}
QLabel#tile_badge {{ background: transparent; }}
QFrame#hero {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 10px; }}
QLabel#hero_title {{ font-family: 'Bahnschrift', 'Segoe UI'; font-size: 24px; font-weight: 600; }}
QLabel#hero_sub {{ color: {t['muted']}; font-size: 13px; }}
QLabel#hero_stat {{ color: {t['muted']}; font-size: 12px; }}
QTabWidget::pane {{ border: 1px solid {t['border']}; background: {t['sidebar']}; }}
QTabBar::tab {{ background: {t['card']}; border: 1px solid {t['border']}; padding: 6px 14px; }}
QTabBar::tab:selected {{ background: {t['card_sel']}; color: {t['accent']}; }}
QTabBar::tab:hover {{ color: {t['accent']}; }}
QListWidget#toolsnav {{ background: {t['sidebar']}; border: none; outline: 0; border-right: 1px solid {t['border']}; }}
QListWidget#toolsnav::item {{ padding: 6px 10px; font-size: 13px; border-bottom: 1px solid {t['border']}; }}
QListWidget#toolsnav::item:selected {{ background: {t['card_sel']}; color: {t['accent']}; }}
QListWidget#toolsnav::item:hover {{ background: {t['hover']}; }}
QListWidget#filelist {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px; outline: 0; }}
QListWidget#filelist::item {{ padding: 6px 8px; }}
QListWidget#filelist::item:selected {{ background: {t['selection']}; color: {t['selection_text']}; }}
QListWidget#filelist::item:hover {{ background: {t['hover']}; }}
QListWidget#filelist::indicator {{ width: 15px; height: 15px; border: 1px solid {t['border']}; border-radius: 4px; background: {check_fill}; }}
QListWidget#filelist::indicator:hover {{ border-color: {t['accent']}; }}
QListWidget#filelist::indicator:checked {{ background: {check_fill}; border-color: {t['accent']}; image: url({check_icon}); }}
QPushButton#toolsbtn {{ background: {t['card']}; border: 1px solid {t['border']}; padding: 6px 12px; font-size: 13px; }}
QPushButton#toolsbtn:hover {{ border-color: {t['accent']}; color: {t['accent']}; }}
QTreeWidget#modtree {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px; outline: 0; }}
QTreeWidget#modtree::item {{ padding: 3px 2px; }}
QTreeWidget#modtree::item:selected {{ background: {t['selection']}; color: {t['selection_text']}; }}
QTextBrowser#modreport {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 4px; padding: 8px; font-size: 13px; }}
"""


# ===========================================================================
# Потоки
# ===========================================================================
_live_threads: set[QThread] = set()


def spawn(thread: QThread) -> QThread:
    for t in list(_live_threads):
        if t.isFinished():
            _live_threads.discard(t)
    _live_threads.add(thread)
    thread.finished.connect(lambda t=thread: _live_threads.discard(t))
    thread.start()
    return thread


# ===========================================================================
# Утилиты
# ===========================================================================
def offline_uuid(name: str) -> str:
    h = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode("utf-8")).digest())
    h[6] = (h[6] & 0x0F) | 0x30
    h[8] = (h[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


_TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e", "ж": "zh",
    "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o",
    "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f", "х": "kh", "ц": "ts",
    "ч": "ch", "ш": "sh", "щ": "shch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu",
    "я": "ya", "і": "i", "ї": "yi", "є": "ye", "ґ": "g",
}


def slugify(name: str) -> str:
    low = "".join(_TRANSLIT.get(ch, ch) for ch in name.strip().lower())
    s = re.sub(r"[^a-z0-9_-]+", "-", low).strip("-")
    return s or "instance"


def fmt_duration(seconds: int) -> str:
    if seconds < 60:
        return f"{seconds}s"
    if seconds < 3600:
        return f"{seconds // 60}m"
    h = seconds // 3600
    m = (seconds % 3600) // 60
    return f"{h}h {m}m"


def fmt_size(num_bytes: int) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if num_bytes < 1024:
            return f"{num_bytes:.1f} {unit}"
        num_bytes /= 1024
    return f"{num_bytes:.1f} PB"


def dir_size(path: Path) -> int:
    total = 0
    try:
        for root, dirs, files in os.walk(path):
            for f in files:
                try:
                    total += (Path(root) / f).stat().st_size
                except OSError:
                    pass
    except OSError:
        pass
    return total


def count_mods(folder: Path) -> int:
    mods_dir = folder / "mods"
    if not mods_dir.exists():
        return 0
    try:
        return sum(1 for f in mods_dir.iterdir() if f.is_file() and f.suffix.lower() == ".jar")
    except OSError:
        return 0


def sha1_file(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sha512_file(path: Path) -> str:
    h = hashlib.sha512()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def safe_join(base: Path, *paths) -> Path:
    base = base.resolve()
    dest = base.joinpath(*paths).resolve()
    try:
        dest.relative_to(base)
    except ValueError:
        raise RuntimeError(f"Unsafe path: {dest}")
    return dest


def fmt_num(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 1_000:
        return f"{n // 1_000}K"
    return str(n)


def short_text(text: str, limit: int = 120) -> str:
    text = " ".join((text or "").split())
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "..."


def loader_badge(loader: str, size: int = 44) -> QPixmap:
    bg, fg = LOADER_COLORS.get(loader, ("#3a4150", "#dfe3ea"))
    pix = QPixmap(size, size)
    pix.fill(Qt.GlobalColor.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(QColor(bg))
    p.setPen(Qt.PenStyle.NoPen)
    p.drawRoundedRect(0, 0, size, size, 8, 8)
    p.setPen(QColor(fg))
    p.setFont(QFont("Bahnschrift", int(size * 0.42), QFont.Weight.Bold))
    letter = {"Vanilla": "V", "Paper": "P", "Fabric": "F", "Quilt": "Q",
              "Forge": "Fo", "NeoForge": "NF"}.get(loader, "?")
    p.drawText(pix.rect(), Qt.AlignmentFlag.AlignCenter, letter)
    p.end()
    return pix


def instance_icon_pixmap(inst, size: int = 44) -> QPixmap:
    custom = inst.folder / "icon.png"
    if custom.exists():
        pix = QPixmap(str(custom))
        if not pix.isNull():
            return pix.scaled(size, size, Qt.AspectRatioMode.KeepAspectRatio,
                              Qt.TransformationMode.SmoothTransformation)
    return loader_badge(inst.loader, size)


META_DIRNAME = "frimer_meta"
_FILE_META_CACHE: dict = {}


def meta_paths(folder: Path, filename: str):
    d = folder.parent / META_DIRNAME / folder.name
    return d / f"{filename}.json", d / f"{filename}.icon"


def save_install_meta(folder: Path, filename: str, title: str, icon_url: str | None):
    try:
        jp, ip = meta_paths(folder, filename)
        jp.parent.mkdir(parents=True, exist_ok=True)
        jp.write_text(json.dumps({"title": title}, ensure_ascii=False), encoding="utf-8")
        if icon_url:
            r = requests.get(icon_url, timeout=10, headers=UA)
            if r.status_code == 200 and r.content:
                ip.write_bytes(r.content)
    except Exception:
        pass


def load_install_meta(folder: Path, filename: str):
    title, icon = None, None
    try:
        jp, ip = meta_paths(folder, filename)
        if jp.exists():
            title = json.loads(jp.read_text(encoding="utf-8")).get("title")
        if ip.exists():
            icon = ip.read_bytes()
    except (OSError, ValueError):
        pass
    return title, icon


def delete_install_meta(folder: Path, filename: str):
    try:
        for f in meta_paths(folder, filename):
            f.unlink(missing_ok=True)
    except OSError:
        pass


def read_file_meta(path: Path):
    try:
        st = path.stat()
        key = (str(path), st.st_mtime_ns, st.st_size)
    except OSError:
        return None, None
    if key in _FILE_META_CACHE:
        return _FILE_META_CACHE[key]

    title, icon = None, None
    try:
        with zipfile.ZipFile(path) as z:
            names = set(z.namelist())

            def pick_icon(ref):
                if isinstance(ref, dict) and ref:
                    try:
                        ref = ref[max(ref, key=lambda k: int(k))]
                    except (ValueError, TypeError):
                        ref = next(iter(ref.values()))
                if isinstance(ref, str):
                    ref = ref.lstrip("/")
                    if ref in names:
                        return z.read(ref)
                return None

            def read_json(name):
                return json.loads(z.read(name).decode("utf-8-sig"), strict=False)

            if "fabric.mod.json" in names:
                d = read_json("fabric.mod.json")
                title = d.get("name")
                icon = pick_icon(d.get("icon"))
            elif "quilt.mod.json" in names:
                md = read_json("quilt.mod.json").get("quilt_loader", {}).get("metadata", {})
                title = md.get("name")
                icon = pick_icon(md.get("icon"))
            else:
                for toml_name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                    if toml_name in names and tomllib:
                        d = tomllib.loads(z.read(toml_name).decode("utf-8", errors="replace"))
                        mods = d.get("mods") or []
                        if mods:
                            title = mods[0].get("displayName")
                            icon = pick_icon(mods[0].get("logoFile") or d.get("logoFile"))
                        break
            if icon is None and "pack.png" in names:
                icon = z.read("pack.png")
    except Exception:
        pass
    if title is not None and not isinstance(title, str):
        title = None
    _FILE_META_CACHE[key] = (title, icon)
    return title, icon


def pixmap_from_bytes(data, size: int) -> QPixmap | None:
    if not data:
        return None
    pix = QPixmap()
    if not pix.loadFromData(data):
        return None
    return pix.scaled(size, size, Qt.AspectRatioMode.KeepAspectRatio,
                      Qt.TransformationMode.SmoothTransformation)


def letter_icon(text: str, size: int = 40) -> QPixmap:
    pix = QPixmap(size, size)
    pix.fill(Qt.GlobalColor.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(QColor("#3a4150"))
    p.setPen(Qt.PenStyle.NoPen)
    p.drawRoundedRect(0, 0, size, size, 8, 8)
    p.setPen(QColor("#f2a93b"))
    p.setFont(QFont("Bahnschrift", int(size * 0.42), QFont.Weight.Bold))
    p.drawText(pix.rect(), Qt.AlignmentFlag.AlignCenter, (text.strip()[:1] or "?").upper())
    p.end()
    return pix


def checkmark_icon_path(color_hex: str) -> str:
    cache_dir = GAME_DIR / "cache"
    cache_dir.mkdir(parents=True, exist_ok=True)
    path = cache_dir / f"check_{color_hex.lstrip('#')}.png"
    if not path.exists():
        size = 24
        pix = QPixmap(size, size)
        pix.fill(Qt.GlobalColor.transparent)
        p = QPainter(pix)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        pen = QPen(QColor(color_hex))
        pen.setWidthF(size * 0.16)
        pen.setCapStyle(Qt.PenCapStyle.RoundCap)
        pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
        p.setPen(pen)
        p.drawPolyline(QPolygonF([
            QPointF(size * 0.20, size * 0.54),
            QPointF(size * 0.42, size * 0.76),
            QPointF(size * 0.82, size * 0.26),
        ]))
        p.end()
        pix.save(str(path), "PNG")
    return str(path).replace("\\", "/")


def field_label(text: str) -> QLabel:
    label = QLabel(text)
    label.setObjectName("field")
    return label


# ===========================================================================
# Скины
# ===========================================================================
def _skins_index_path() -> Path:
    return SKINS_DIR / "index.json"


def load_skins_index() -> list:
    p = _skins_index_path()
    if not p.exists():
        return []
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []


def _save_skins_index(items: list):
    SKINS_DIR.mkdir(parents=True, exist_ok=True)
    _skins_index_path().write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding="utf-8")


def add_local_skin(data: bytes, name: str, source: str = "") -> dict:
    SKINS_DIR.mkdir(parents=True, exist_ok=True)
    sid = uuid.uuid4().hex[:12]
    fname = f"{sid}.png"
    (SKINS_DIR / fname).write_bytes(data)
    entry = {"id": sid, "name": (name or "").strip()[:32] or "Скин", "file": fname,
             "source": source, "added": int(time.time())}
    items = load_skins_index()
    items.append(entry)
    _save_skins_index(items)
    return entry


def delete_local_skin(skin_id: str):
    items = load_skins_index()
    keep = []
    for entry in items:
        if entry.get("id") == skin_id:
            (SKINS_DIR / entry["file"]).unlink(missing_ok=True)
        else:
            keep.append(entry)
    _save_skins_index(keep)


def fetch_skin_by_username(name: str) -> bytes:
    r = requests.get(f"{MOJANG_PROFILE_API}/{name}", headers=UA, timeout=10)
    if r.status_code == 404:
        raise RuntimeError(f"Игрок «{name}» не найден — нет лицензионного аккаунта с таким ником.")
    if r.status_code != 200:
        raise RuntimeError(f"Mojang API вернул ошибку {r.status_code}.")
    profile_id = (r.json() or {}).get("id")
    if not profile_id:
        raise RuntimeError("Mojang API не вернул UUID игрока.")

    r2 = requests.get(f"{MOJANG_SESSION_API}/{profile_id}", headers=UA, timeout=10)
    if r2.status_code != 200:
        raise RuntimeError(f"Mojang session API вернул ошибку {r2.status_code}.")
    props = (r2.json() or {}).get("properties", [])
    textures_b64 = next((pr["value"] for pr in props if pr.get("name") == "textures"), None)
    if not textures_b64:
        raise RuntimeError("У игрока нет данных о текстурах.")
    try:
        tex = json.loads(base64.b64decode(textures_b64))
    except (ValueError, TypeError) as e:
        raise RuntimeError("Не удалось разобрать данные текстур.") from e
    skin_url = (tex.get("textures", {}).get("SKIN") or {}).get("url")
    if not skin_url:
        raise RuntimeError(f"У игрока «{name}» стандартный скин (Steve/Alex по умолчанию).")
    r3 = requests.get(skin_url, headers=UA, timeout=15)
    if r3.status_code != 200 or not r3.content:
        raise RuntimeError("Не удалось скачать файл скина.")
    return r3.content


def _skin_crop(img: QImage, x: int, y: int, w: int, h: int, mirror: bool = False) -> QPixmap:
    part = img.copy(x, y, w, h)
    if mirror:
        part = part.mirrored(True, False)
    return QPixmap.fromImage(part)


def render_skin_preview(data: bytes, size: int = 128) -> QPixmap | None:
    img = QImage()
    if not img.loadFromData(data) or img.width() < 64:
        return None
    img = img.convertToFormat(QImage.Format.Format_ARGB32)
    legacy = img.height() <= 32

    s = max(1, size // 32)
    canvas = QPixmap(16 * s, 32 * s)
    canvas.fill(Qt.GlobalColor.transparent)
    p = QPainter(canvas)

    def blit(part: QPixmap, x: int, y: int, w: int, h: int):
        if part.isNull():
            return
        p.drawPixmap(x, y, part.scaled(w, h, Qt.AspectRatioMode.IgnoreAspectRatio,
                                       Qt.TransformationMode.FastTransformation))

    head = _skin_crop(img, 8, 8, 8, 8)
    torso = _skin_crop(img, 20, 20, 8, 12)
    r_arm = _skin_crop(img, 44, 20, 4, 12)
    r_leg = _skin_crop(img, 4, 20, 4, 12)
    if legacy:
        l_arm = _skin_crop(img, 44, 20, 4, 12, mirror=True)
        l_leg = _skin_crop(img, 4, 20, 4, 12, mirror=True)
    else:
        l_arm = _skin_crop(img, 36, 52, 4, 12)
        l_leg = _skin_crop(img, 20, 52, 4, 12)

    blit(head, 4 * s, 0, 8 * s, 8 * s)
    blit(l_arm, 0, 8 * s, 4 * s, 12 * s)
    blit(torso, 4 * s, 8 * s, 8 * s, 12 * s)
    blit(r_arm, 12 * s, 8 * s, 4 * s, 12 * s)
    blit(l_leg, 4 * s, 20 * s, 4 * s, 12 * s)
    blit(r_leg, 8 * s, 20 * s, 4 * s, 12 * s)

    if not legacy:
        blit(_skin_crop(img, 40, 8, 8, 8), 4 * s, 0, 8 * s, 8 * s)
        blit(_skin_crop(img, 52, 52, 4, 12), 0, 8 * s, 4 * s, 12 * s)
        blit(_skin_crop(img, 20, 36, 8, 12), 4 * s, 8 * s, 8 * s, 12 * s)
        blit(_skin_crop(img, 44, 36, 4, 12), 12 * s, 8 * s, 4 * s, 12 * s)
        blit(_skin_crop(img, 4, 52, 4, 12), 4 * s, 20 * s, 4 * s, 12 * s)
        blit(_skin_crop(img, 4, 36, 4, 12), 8 * s, 20 * s, 4 * s, 12 * s)

    p.end()
    return canvas


def export_skin_to_customskinloader(nickname: str, data: bytes, instances: list) -> list:
    written = []
    safe_nick = re.sub(r"[^A-Za-z0-9_]", "_", nickname)[:16] or "Player"
    for inst in instances:
        try:
            folder = inst.folder / "CustomSkinLoader" / "LocalSkin" / "skins"
            folder.mkdir(parents=True, exist_ok=True)
            path = folder / f"{safe_nick}.png"
            path.write_bytes(data)
            written.append(path)
        except OSError:
            continue
    return written


# ===========================================================================
# Скачивание
# ===========================================================================
def http_get(url, **kw):
    try:
        return requests.get(url, timeout=30, headers=UA, **kw)
    except requests.RequestException as e:
        raise RuntimeError(f"Не могу подключиться: {e}") from e


def download_file(url: str, dest: Path, sha1: str | None = None,
                  sha512: str | None = None, log=None, progress=None) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        if sha1 and sha1_file(dest).lower() == sha1.lower():
            return dest
        if sha512 and sha512_file(dest).lower() == sha512.lower():
            return dest
        if not sha1 and not sha512:
            return dest

    tmp = dest.with_suffix(dest.suffix + ".part")
    headers = dict(UA)
    mode = "wb"
    if tmp.exists():
        headers["Range"] = f"bytes={tmp.stat().st_size}-"
        mode = "ab"
    try:
        with requests.get(url, stream=True, timeout=60, headers=headers) as r:
            if r.status_code == 416:
                tmp.replace(dest)
            else:
                r.raise_for_status()
                done = 0
                if mode == "ab":
                    if r.status_code == 206:
                        done = tmp.stat().st_size
                    else:
                        mode = "wb"
                length = int(r.headers.get("Content-Length") or 0)
                total = done + length if length else 0
                with open(tmp, mode) as f:
                    for chunk in r.iter_content(1 << 16):
                        f.write(chunk)
                        done += len(chunk)
                        if progress and total:
                            progress(done, total)
                tmp.replace(dest)
    except requests.RequestException as e:
        raise RuntimeError(f"Скачивание не удалось: {url} ({e})") from e

    if sha1 and sha1_file(dest).lower() != sha1.lower():
        dest.unlink(missing_ok=True)
        raise RuntimeError(f"SHA1 mismatch: {dest.name}")
    if sha512 and sha512_file(dest).lower() != sha512.lower():
        dest.unlink(missing_ok=True)
        raise RuntimeError(f"SHA512 mismatch: {dest.name}")
    return dest


# ===========================================================================
# Mojang API
# ===========================================================================
_manifest_cache: dict | None = None


def get_version_manifest() -> dict:
    global _manifest_cache
    if _manifest_cache is None:
        r = http_get(MANIFEST_URL)
        r.raise_for_status()
        _manifest_cache = r.json()
    return _manifest_cache


def get_release_versions() -> list[str]:
    m = get_version_manifest()
    return [v["id"] for v in m["versions"] if v["type"] == "release"]


def get_version_info(version_id: str) -> dict:
    m = get_version_manifest()
    for v in m["versions"]:
        if v["id"] == version_id:
            r = http_get(v["url"])
            r.raise_for_status()
            return r.json()
    raise RuntimeError(f"Версия {version_id} не найдена в манифесте Mojang")


def asset_url(hash_: str) -> str:
    return f"{RESOURCES_URL}/{hash_[:2]}/{hash_}"


def ensure_launcher_profile(game_dir: Path):
    profile_file = game_dir / "launcher_profiles.json"
    if profile_file.exists():
        return
    profiles = {
        "profiles": {
            "Frimer": {
                "name": "Frimer",
                "type": "custom",
                "gameDir": str(game_dir),
                "lastVersionId": "latest-release",
                "created": "2024-01-01T00:00:00.000Z",
                "lastUsed": "2024-01-01T00:00:00.000Z",
                "icon": "Furnace_icon",
            }
        },
        "settings": {"enableSnapshots": False},
        "version": 3,
    }
    game_dir.mkdir(parents=True, exist_ok=True)
    profile_file.write_text(json.dumps(profiles, ensure_ascii=False, indent=2), encoding="utf-8")


# ===========================================================================
# Java
# ===========================================================================
JAVA_LTS = (8, 11, 17, 21, 25)


def _lts_at_least(major: int) -> int:
    for v in JAVA_LTS:
        if v >= major:
            return v
    return major


def java_major_for_mc(mc: str) -> int:
    try:
        vj = VERSIONS_DIR / mc / f"{mc}.json"
        if vj.exists():
            major = json.loads(vj.read_text(encoding="utf-8")).get("javaVersion", {}).get("majorVersion")
            if isinstance(major, int) and major > 0:
                return _lts_at_least(major)
    except (OSError, ValueError):
        pass
    lead = re.match(r"^(\d+)", mc)
    if lead and int(lead.group(1)) >= 26:
        return 25
    try:
        parts = mc.split(".")
        minor = int(parts[1]) if len(parts) > 1 else 0
        patch = int(parts[2]) if len(parts) > 2 else 0
    except (ValueError, IndexError):
        return 17
    if minor >= 21 or (minor == 20 and patch >= 5):
        return 21
    if minor >= 17:
        return 17
    return 8


def _java_major(java_exe) -> int | None:
    try:
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        out = subprocess.run([str(java_exe), "-version"], capture_output=True, text=True,
                             timeout=20, creationflags=flags)
        txt = (out.stderr or out.stdout or "").lower()
        m = re.search(r'version "?(\d+)(?:\.(\d+))?', txt)
        if m:
            first = int(m.group(1))
            return int(m.group(2) or 0) if first == 1 else first
    except Exception:
        pass
    return None


def _find_java_exe(java_dir: Path) -> Path | None:
    name = "java.exe" if os.name == "nt" else "java"
    if java_dir.exists():
        for c in java_dir.rglob(name):
            if c.is_file() and c.parent.name == "bin":
                return c
    return None


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _download_java(needed: int, java_dir: Path, log, progress) -> Path:
    os_name = {"nt": "windows", "posix": "linux"}.get(os.name, "linux")
    if sys.platform == "darwin":
        os_name = "mac"
    machine = platform.machine().lower()
    if machine in ("arm64", "aarch64"):
        arch = "aarch64"
    else:
        arch = "x64" if sys.maxsize > 2**32 else "x32"

    assets = None
    for image_type in ("jre", "jdk"):
        api = (f"{ADOPTIUM_API}/assets/latest/{needed}/hotspot"
               f"?architecture={arch}&image_type={image_type}&os={os_name}&vendor=eclipse")
        r = http_get(api)
        if r.status_code == 200 and r.json():
            assets = r.json()
            break
    if not assets:
        raise RuntimeError(f"Adoptium не вернул Java {needed} для {os_name}/{arch}")

    pkg = assets[0]["binary"]["package"]
    url, name, checksum = pkg["link"], pkg["name"], pkg.get("checksum")
    log(f"Скачиваю Java {needed} (Eclipse Temurin, {name})...")
    archive = RUNTIME_DIR / name
    download_file(url, archive, log=log, progress=progress)
    if checksum and _sha256_file(archive).lower() != checksum.lower():
        archive.unlink(missing_ok=True)
        raise RuntimeError("контрольная сумма Java не совпала")

    tmp_dir = RUNTIME_DIR / f"jdk-{needed}.tmp"
    shutil.rmtree(tmp_dir, ignore_errors=True)
    tmp_dir.mkdir(parents=True)
    log("Распаковываю Java...")
    try:
        if name.endswith(".zip"):
            with zipfile.ZipFile(archive) as z:
                z.extractall(tmp_dir)
        elif name.endswith((".tar.gz", ".tgz")):
            with tarfile.open(archive) as t:
                try:
                    t.extractall(tmp_dir, filter="data")
                except TypeError:
                    t.extractall(tmp_dir)
        else:
            raise RuntimeError(f"неизвестный формат архива Java: {name}")
    except Exception:
        shutil.rmtree(tmp_dir, ignore_errors=True)
        raise
    finally:
        archive.unlink(missing_ok=True)

    shutil.rmtree(java_dir, ignore_errors=True)
    tmp_dir.replace(java_dir)
    exe = _find_java_exe(java_dir)
    if exe is not None:
        try:
            os.chmod(exe, 0o755)
        except OSError:
            pass
    if exe is None or _java_major(exe) is None:
        shutil.rmtree(java_dir, ignore_errors=True)
        raise RuntimeError("Java не запускается после распаковки")
    log(f"Java {needed} установлена: {exe}")
    return exe


def ensure_java(mc_version: str, log, progress=None) -> Path:
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    needed = java_major_for_mc(mc_version)
    java_dir = RUNTIME_DIR / f"jdk-{needed}"

    exe = _find_java_exe(java_dir)
    if exe is not None:
        if _java_major(exe) is not None:
            log(f"Java {needed} уже есть: {exe}")
            return exe
        log("Скачанная Java повреждена, качаю заново...")
        shutil.rmtree(java_dir, ignore_errors=True)

    try:
        return _download_java(needed, java_dir, log, progress)
    except Exception as e:
        system_java = shutil.which("java")
        if system_java and _java_major(system_java) == needed:
            log(f"Не удалось скачать Java ({e}). Использую системную {needed}: {system_java}")
            return Path(system_java)
        raise RuntimeError(f"Не удалось скачать Java {needed}: {e}") from e


# ===========================================================================
# Установка Vanilla
# ===========================================================================
class VanillaInstaller:
    def __init__(self, log_cb, progress_cb, version_id: str):
        self.log = log_cb
        self.progress = progress_cb
        self.version_id = version_id

    def install(self) -> Path:
        VERSIONS_DIR.mkdir(parents=True, exist_ok=True)
        LIBRARIES_DIR.mkdir(parents=True, exist_ok=True)
        ASSETS_DIR.mkdir(parents=True, exist_ok=True)

        version_dir = VERSIONS_DIR / self.version_id
        version_json = version_dir / f"{self.version_id}.json"
        version_jar = version_dir / f"{self.version_id}.jar"

        if version_json.exists() and version_jar.exists():
            self.log(f"Vanilla {self.version_id} уже установлена")
            return version_json

        info = get_version_info(self.version_id)
        version_dir.mkdir(parents=True, exist_ok=True)
        version_json.write_text(json.dumps(info, indent=2, ensure_ascii=False), encoding="utf-8")

        downloads = info.get("downloads", {})
        client = downloads.get("client")
        if client and not version_jar.exists():
            self.log(f"Скачиваю клиент {self.version_id}...")
            download_file(client["url"], version_jar, sha1=client.get("sha1"), log=self.log)

        libs = info.get("libraries", [])
        total = len(libs)
        for i, lib in enumerate(libs, 1):
            rules = lib.get("rules")
            if rules and not _rules_allow_os(rules):
                continue
            artifact = lib.get("downloads", {}).get("artifact")
            if not artifact:
                continue
            path = LIBRARIES_DIR / artifact["path"]
            try:
                download_file(artifact["url"], path, sha1=artifact.get("sha1"), log=self.log)
            except RuntimeError as e:
                self.log(f"Не скачалось {path.name}: {e}")
            self.progress(i, total)

        asset_index = info.get("assetIndex")
        if asset_index:
            index_path = ASSETS_DIR / "indexes" / f"{asset_index['id']}.json"
            download_file(asset_index["url"], index_path,
                          sha1=asset_index.get("sha1"), log=self.log)
            self._install_assets(index_path)

        self.log(f"Vanilla {self.version_id} готова")
        return version_json

    def _install_assets(self, index_path: Path):
        try:
            index = json.loads(index_path.read_text(encoding="utf-8"))
        except Exception:
            return
        objects = index.get("objects", {})
        self.log(f"Объектов ассетов: {len(objects)}")

        def fetch(item):
            _, meta = item
            h = meta["hash"]
            p = ASSETS_DIR / "objects" / h[:2] / h
            if p.exists():
                return
            try:
                download_file(asset_url(h), p, sha1=h, log=lambda _: None)
            except RuntimeError:
                pass

        with ThreadPoolExecutor(max_workers=12) as pool:
            list(pool.map(fetch, objects.items()))


# ===========================================================================
# Fabric / Quilt
# ===========================================================================
def install_fabric_like(kind: str, mc_version: str, loader_version: str | None,
                        log, progress) -> Path:
    if kind == "fabric":
        meta = "https://meta.fabricmc.net/v2"
        if loader_version is None:
            r = http_get(f"{meta}/versions/loader/{mc_version}")
            r.raise_for_status()
            loader_version = r.json()[0]["loader"]["version"]
        profile_url = f"{meta}/versions/loader/{mc_version}/{loader_version}/profile/json"
        version_id = f"fabric-loader-{loader_version}-{mc_version}"
        maven = "https://maven.fabricmc.net/"
    else:
        meta = "https://meta.quiltmc.org/v3"
        if loader_version is None:
            r = http_get(f"{meta}/versions/loader/{mc_version}")
            r.raise_for_status()
            loader_version = r.json()[0]["loader"]["version"]
        profile_url = f"{meta}/versions/loader/{mc_version}/{loader_version}/profile/json"
        version_id = f"quilt-loader-{loader_version}-{mc_version}"
        maven = "https://maven.quiltmc.org/repository/release/"

    log(f"{kind.capitalize()} loader {loader_version} для {mc_version}")
    r = http_get(profile_url)
    r.raise_for_status()
    profile = r.json()

    version_dir = VERSIONS_DIR / version_id
    version_dir.mkdir(parents=True, exist_ok=True)
    (version_dir / f"{version_id}.json").write_text(
        json.dumps(profile, indent=2, ensure_ascii=False), encoding="utf-8")

    libs = profile.get("libraries", [])
    total = len(libs)
    for i, lib in enumerate(libs, 1):
        name = lib.get("name", "")
        url = lib.get("url") or maven
        if not name:
            continue
        try:
            coord = name.split(":")
            group, artifact, ver = coord[0], coord[1], coord[2]
            path = f"{group.replace('.', '/')}/{artifact}/{ver}/{artifact}-{ver}.jar"
            dest = LIBRARIES_DIR / path
            if not dest.exists():
                download_file(f"{url.rstrip('/')}/{path}", dest, log=lambda _: None)
        except Exception as e:
            log(f"Не скачалось {name}: {e}")
        progress(i, total)

    log(f"{kind.capitalize()} {loader_version} готова")
    return version_dir / f"{version_id}.json"


# ===========================================================================
# Forge / NeoForge
# ===========================================================================
def install_forge_like(kind: str, mc_version: str, loader_version: str | None,
                       log, progress) -> Path:
    ensure_launcher_profile(GAME_DIR)

    java = ensure_java(mc_version, log, lambda d, t: progress(d // 1024, t // 1024))

    if kind == "forge":
        if loader_version is None:
            r = http_get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
            r.raise_for_status()
            promos = r.json().get("promos", {})
            loader_version = promos.get(f"{mc_version}-latest") or promos.get(f"{mc_version}-recommended")
            if not loader_version:
                raise RuntimeError(f"Forge для Minecraft {mc_version} не найден")
        installer_url = (f"https://maven.minecraftforge.net/net/minecraftforge/forge/"
                         f"{mc_version}-{loader_version}/forge-{mc_version}-{loader_version}-installer.jar")
        installer_name = f"forge-{mc_version}-{loader_version}-installer.jar"
        version_id = f"{mc_version}-forge-{loader_version}"
    else:
        if loader_version is None:
            loader_version = pick_neoforge_version(mc_version)
        installer_url = (f"https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                         f"{loader_version}/neoforge-{loader_version}-installer.jar")
        installer_name = f"neoforge-{loader_version}-installer.jar"
        version_id = f"neoforge-{loader_version}"

    installer = RUNTIME_DIR / installer_name
    log(f"Скачиваю {installer_name}...")
    download_file(installer_url, installer, log=log)

    log(f"Запускаю установщик {kind.capitalize()} {loader_version}...")
    log(f"Java: {java}")
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    proc = subprocess.Popen(
        [str(java), "-jar", str(installer), "--installClient", str(GAME_DIR)],
        cwd=str(GAME_DIR), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace", creationflags=flags,
    )
    for line in proc.stdout:
        line = line.rstrip()
        if line:
            log(line)
    code = proc.wait()
    if code != 0:
        raise RuntimeError(
            f"Инсталлятор {kind} вернул код {code}.\n"
            f"Проверь Java {java_major_for_mc(mc_version)}+ и повтори попытку."
        )

    json_path = VERSIONS_DIR / version_id / f"{version_id}.json"
    if not json_path.exists():
        for d in VERSIONS_DIR.iterdir():
            if d.is_dir() and kind in d.name.lower() and mc_version in d.name:
                candidate = d / f"{d.name}.json"
                if candidate.exists():
                    json_path = candidate
                    break
    if not json_path.exists():
        raise RuntimeError(f"Инсталлятор отработал, но не нашёл {kind} в versions/")
    log(f"{kind.capitalize()} {loader_version} установлен")
    return json_path


# ===========================================================================
# Запуск игры
# ===========================================================================
def _rules_allow_os(rules: list) -> bool:
    os_name = {"nt": "windows", "posix": "linux"}.get(os.name, "linux")
    if sys.platform == "darwin":
        os_name = "osx"
    allowed = None
    for r in rules:
        action = r.get("action")
        match = True
        if r.get("features"):
            match = False
        r_os = r.get("os")
        if r_os and r_os.get("name") and r_os["name"] != os_name:
            match = False
        if match:
            allowed = (action == "allow")
    return bool(allowed)


def _substitute(s: str, info: dict, game_dir: Path, natives_dir: Path,
                nickname: str) -> str:
    repl = {
        "${auth_player_name}": nickname,
        "${version_name}": info.get("id", ""),
        "${game_directory}": str(game_dir),
        "${assets_root}": str(ASSETS_DIR),
        "${assets_index_name}": info.get("assets", "legacy"),
        "${auth_uuid}": offline_uuid(nickname),
        "${auth_access_token}": "0",
        "${user_type}": "legacy",
        "${version_type}": info.get("type", "release"),
        "${natives_directory}": str(natives_dir),
        "${launcher_name}": "Frimer",
        "${launcher_version}": "2.0",
        "${classpath}": "",
        "${classpath_separator}": ";" if os.name == "nt" else ":",
        "${library_directory}": str(LIBRARIES_DIR),
    }
    for k, v in repl.items():
        s = s.replace(k, v)
    return s


def _resolve_args(args: list, info: dict, game_dir: Path, natives_dir: Path,
                  nickname: str) -> list[str]:
    out = []
    for a in args:
        if isinstance(a, str):
            out.append(_substitute(a, info, game_dir, natives_dir, nickname))
            continue
        if not isinstance(a, dict):
            continue
        rules = a.get("rules")
        if rules and not _rules_allow_os(rules):
            continue
        value = a.get("value")
        if isinstance(value, str):
            out.append(_substitute(value, info, game_dir, natives_dir, nickname))
        elif isinstance(value, list):
            for v in value:
                out.append(_substitute(v, info, game_dir, natives_dir, nickname))
    return out


def _extract_natives(info: dict, natives_dir: Path):
    for lib in info.get("libraries", []):
        rules = lib.get("rules")
        if rules and not _rules_allow_os(rules):
            continue
        downloads = lib.get("downloads", {})
        classifiers = downloads.get("classifiers") if downloads else None
        if not classifiers:
            continue
        os_name = {"nt": "windows", "posix": "linux"}.get(os.name, "linux")
        if sys.platform == "darwin":
            os_name = "osx"
        key = f"natives-{os_name}"
        native = classifiers.get(key)
        if not native:
            continue
        jar_path = LIBRARIES_DIR / native["path"]
        if not jar_path.exists():
            try:
                download_file(native["url"], jar_path, sha1=native.get("sha1"), log=lambda _: None)
            except Exception:
                continue
        try:
            with zipfile.ZipFile(jar_path) as z:
                for name in z.namelist():
                    if name.endswith("/") or name.startswith("META-INF/"):
                        continue
                    target = natives_dir / Path(name).name
                    with z.open(name) as src, open(target, "wb") as dst:
                        shutil.copyfileobj(src, dst)
        except Exception:
            pass


def _lib_key(lib: dict) -> str:
    parts = (lib.get("name") or "").split(":")
    if len(parts) >= 4:
        return f"{parts[0]}:{parts[1]}:{parts[3]}"
    return ":".join(parts[:2])


def _merge_inherited(info: dict) -> dict:
    parent_id = info.get("inheritsFrom")
    if not parent_id:
        return info
    parent_json = VERSIONS_DIR / parent_id / f"{parent_id}.json"
    if not parent_json.exists():
        return info
    parent = _merge_inherited(json.loads(parent_json.read_text(encoding="utf-8")))

    merged = dict(parent)
    merged.update({k: v for k, v in info.items()
                   if k not in ("inheritsFrom", "libraries", "arguments")})

    libs, seen = [], set()
    for lib in info.get("libraries", []) + parent.get("libraries", []):
        key = _lib_key(lib)
        if key and key in seen:
            continue
        seen.add(key)
        libs.append(lib)
    merged["libraries"] = libs

    p_args, c_args = parent.get("arguments") or {}, info.get("arguments") or {}
    if p_args or c_args:
        merged["arguments"] = {
            "jvm": list(p_args.get("jvm", [])) + list(c_args.get("jvm", [])),
            "game": list(p_args.get("game", [])) + list(c_args.get("game", [])),
        }
    return merged


def build_launch_command(version_json: Path, inst_dir: Path, java: Path,
                         nickname: str, ram_mb: int) -> list[str]:
    info = json.loads(version_json.read_text(encoding="utf-8"))
    version_id = info.get("id", version_json.stem)
    version_dir = version_json.parent
    parent_id_for_jar = None

    if "inheritsFrom" in info:
        parent_id_for_jar = info["inheritsFrom"]
        info = _merge_inherited(info)

    classpath = []
    for lib in info.get("libraries", []):
        rules = lib.get("rules")
        if rules and not _rules_allow_os(rules):
            continue
        downloads = lib.get("downloads", {})
        artifact = downloads.get("artifact") if downloads else None
        if artifact:
            classpath.append(str(LIBRARIES_DIR / artifact["path"]))
            continue
        name = lib.get("name")
        url = lib.get("url")
        if name:
            parts = name.split(":")
            if len(parts) >= 3:
                g, a, v = parts[0], parts[1], parts[2]
                classifier = parts[3] if len(parts) > 3 else None
                path = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}"
                if classifier:
                    path += f"-{classifier}"
                path += ".jar"
                local = LIBRARIES_DIR / path
                if local.exists():
                    classpath.append(str(local))
                elif url:
                    try:
                        download_file(f"{url.rstrip('/')}/{path}", local, log=lambda _: None)
                        classpath.append(str(local))
                    except Exception:
                        pass

    client_jar = version_dir / f"{version_id}.jar"
    if not client_jar.exists() and parent_id_for_jar:
        client_jar = VERSIONS_DIR / parent_id_for_jar / f"{parent_id_for_jar}.jar"
    classpath.append(str(client_jar))

    sep = ";" if os.name == "nt" else ":"
    cp = sep.join(classpath)

    asset_index_id = info.get("assets", "legacy")
    asset_index_path = ASSETS_DIR / "indexes" / f"{asset_index_id}.json"
    if not asset_index_path.exists():
        ai = info.get("assetIndex")
        if ai and ai.get("url"):
            download_file(ai["url"], asset_index_path, sha1=ai.get("sha1"), log=lambda _: None)

    main_class = info.get("mainClass")
    if not main_class:
        raise RuntimeError("В version JSON нет mainClass")

    natives_dir = inst_dir / "natives"
    natives_dir.mkdir(parents=True, exist_ok=True)
    _extract_natives(info, natives_dir)

    args = info.get("arguments")
    if args:
        jvm_args = _resolve_args(args.get("jvm", []), info, inst_dir, natives_dir, nickname)
        game_args = _resolve_args(args.get("game", []), info, inst_dir, natives_dir, nickname)
    else:
        jvm_args = []
        legacy = info.get("minecraftArguments", "")
        game_args = legacy.split() if legacy else []

    jvm_args = [
        str(java),
        "-XX:+IgnoreUnrecognizedVMOptions",
        f"-Xmx{ram_mb}M",
        f"-Xms{ram_mb // 2}M",
        f"-Djava.library.path={natives_dir}",
        "-Dminecraft.launcher.brand=Frimer",
        "-Dminecraft.launcher.version=2.0",
    ] + [a for a in jvm_args if not a.startswith("-Djava.library.path") and a not in ("-cp", cp, "")] + [
        "-cp", cp,
        main_class,
    ]

    if not game_args:
        game_args = ["--username", nickname, "--version", version_id,
                     "--gameDir", str(inst_dir), "--assetsDir", str(ASSETS_DIR),
                     "--assetIndex", asset_index_id, "--uuid", offline_uuid(nickname),
                     "--accessToken", "0", "--userType", "legacy"]

    return jvm_args + game_args


# ===========================================================================
# Instance
# ===========================================================================
class Instance:
    def __init__(self, folder: Path, name, loader, mc_version, ram_mb,
                 loader_version: str | None = None):
        self.folder = folder
        self.name = name
        self.loader = loader
        self.mc_version = mc_version
        self.ram_mb = int(ram_mb)
        self.loader_version = loader_version

    def save(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        data = {
            "name": self.name, "loader": self.loader,
            "mc_version": self.mc_version, "ram_mb": self.ram_mb,
        }
        if self.loader_version:
            data["loader_version"] = self.loader_version
        (self.folder / "instance.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    def stats_file(self) -> Path:
        return self.folder / "stats.json"

    def get_stats(self) -> dict:
        f = self.stats_file()
        if not f.exists():
            return {"seconds": 0, "sessions": 0, "last": None}
        try:
            return json.loads(f.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {"seconds": 0, "sessions": 0, "last": None}

    def add_session(self, seconds: int):
        s = self.get_stats()
        s["seconds"] = int(s.get("seconds", 0)) + int(seconds)
        s["sessions"] = int(s.get("sessions", 0)) + 1
        s["last"] = datetime.now().strftime("%Y-%m-%d %H:%M")
        try:
            self.stats_file().write_text(json.dumps(s, ensure_ascii=False, indent=2), encoding="utf-8")
        except OSError:
            pass

    @classmethod
    def load(cls, folder: Path):
        f = folder / "instance.json"
        if not f.exists():
            return None
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
            return cls(folder, d.get("name", folder.name), d.get("loader", "Vanilla"),
                       d.get("mc_version", ""), d.get("ram_mb", 4096),
                       d.get("loader_version"))
        except (OSError, json.JSONDecodeError):
            return None


def load_all_instances() -> list:
    INSTANCES_DIR.mkdir(parents=True, exist_ok=True)
    out = []
    for child in sorted(INSTANCES_DIR.iterdir()):
        if child.is_dir():
            inst = Instance.load(child)
            if inst:
                out.append(inst)
    return out


def duplicate_instance(inst: Instance) -> Instance | None:
    base = slugify(inst.name + "-copy")
    folder = INSTANCES_DIR / base
    n = 2
    while folder.exists():
        folder = INSTANCES_DIR / f"{base}-{n}"
        n += 1
    try:
        shutil.copytree(inst.folder, folder, ignore=shutil.ignore_patterns("logs", "crash-reports"))
    except OSError:
        return None
    new = Instance.load(folder)
    if new:
        new.name = inst.name + " (copy)"
        new.save()
    return new


# ===========================================================================
# Launcher update check
# ===========================================================================
class LauncherUpdateCheckWorker(QThread):
    found = pyqtSignal(str, str, str)

    def run(self):
        if not UPDATE_MANIFEST_URL:
            return
        try:
            r = requests.get(UPDATE_MANIFEST_URL, timeout=10)
            r.raise_for_status()
            data = r.json()
            ver = str(data.get("version", "")).strip()
            url = str(data.get("url", "")).strip()
            notes = str(data.get("notes", "")).strip()
            if ver and _num_key(ver) > _num_key(APP_VERSION):
                self.found.emit(ver, url, notes)
        except Exception:
            pass


def find_playit() -> Path | None:
    for p in PLAYIT_PATHS:
        try:
            if p.is_file():
                return p
        except OSError:
            continue
    own = GAME_DIR / "playit" / "playit.exe"
    return own if own.is_file() else None


def ensure_playit(log=print, progress=None) -> Path:
    found = find_playit()
    if found is not None:
        return found
    dest = GAME_DIR / "playit" / "playit.exe"
    log("playit.gg не найден - скачиваю агент...")
    download_file(PLAYIT_DOWNLOAD_URL, dest, log=log, progress=progress)
    log("playit.gg скачан")
    return dest


def unique_server_folder(name: str) -> Path:
    base = slugify(name)
    folder = SERVERS_DIR / base
    n = 2
    while folder.exists():
        folder = SERVERS_DIR / f"{base}-{n}"
        n += 1
    return folder


class ServerInstance:
    def __init__(self, folder: Path, name: str, mc_version: str, core: str,
                 ram_mb: int, port: int = 25565, playit_address: str = "",
                 auto_playit: bool = True, eula: bool = True,
                 loader_version: str = ""):
        self.folder = folder
        self.name = name
        self.mc_version = mc_version
        self.core = core
        self.ram_mb = int(ram_mb)
        self.port = int(port)
        self.playit_address = playit_address
        self.auto_playit = bool(auto_playit)
        self.eula = bool(eula)
        self.loader_version = loader_version or ""

    @property
    def core_label(self) -> str:
        return CORE_NAMES.get(self.core, self.core.capitalize())

    @property
    def jar_path(self) -> Path:
        if self.core == "paper":
            return self.folder / "paper.jar"
        if self.core == "fabric":
            return self.folder / "fabric-server-launch.jar"
        return self.folder / "server.jar"

    def launch_args(self) -> list | None:
        if self.core in ("forge", "neoforge"):
            return find_forge_like_launch(self.folder, self.core)
        if not self.jar_path.exists():
            return None
        return ["-jar", str(self.jar_path), "--nogui"]

    def save(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        data = {
            "name": self.name, "mc_version": self.mc_version, "core": self.core,
            "ram_mb": self.ram_mb, "port": self.port,
            "playit_address": self.playit_address,
            "auto_playit": self.auto_playit, "eula": self.eula,
            "loader_version": self.loader_version,
        }
        (self.folder / "server.json").write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    @classmethod
    def load(cls, folder: Path):
        f = folder / "server.json"
        if not f.exists():
            return None
        try:
            d = json.loads(f.read_text(encoding="utf-8"))
            return cls(
                folder,
                d.get("name", folder.name),
                d.get("mc_version", ""),
                d.get("core", "paper"),
                d.get("ram_mb", 4096),
                d.get("port", 25565),
                d.get("playit_address", ""),
                d.get("auto_playit", True),
                d.get("eula", True),
                d.get("loader_version", ""),
            )
        except (OSError, json.JSONDecodeError):
            return None


def load_all_servers() -> list:
    SERVERS_DIR.mkdir(parents=True, exist_ok=True)
    out = []
    for child in sorted(SERVERS_DIR.iterdir()):
        if child.is_dir():
            srv = ServerInstance.load(child)
            if srv:
                out.append(srv)
    return out


class PaperInstaller(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    done = pyqtSignal(str)
    failed = pyqtSignal(str)

    def __init__(self, mc_version: str, dest: Path):
        super().__init__()
        self.mc_version = mc_version
        self.dest = dest

    def run(self):
        try:
            self.status.emit(f"Смотрю версии Paper для {self.mc_version}...")
            r = requests.get(PAPER_API, headers=PAPER_UA, timeout=30)
            if r.status_code != 200:
                raise RuntimeError(f"PaperMC API вернул {r.status_code}.")
            data = r.json()
            raw = data.get("versions", {})
            all_versions = []
            if isinstance(raw, dict):
                for group in raw.values():
                    all_versions.extend(group)
            else:
                all_versions = raw
            ids = []
            for v in all_versions:
                if isinstance(v, dict):
                    ids.append(v.get("id") or v.get("version"))
                elif isinstance(v, str):
                    ids.append(v)
            ids = [v for v in ids if v]

            candidates = [self.mc_version]
            parts = self.mc_version.split(".")
            if len(parts) >= 2:
                try:
                    major, minor = int(parts[0]), int(parts[1])
                    for delta in (1, 2, 3):
                        if minor - delta > 0:
                            candidates.append(f"{major}.{minor - delta}")
                except ValueError:
                    pass
            chosen = next((c for c in candidates if c in ids), None)
            if not chosen:
                raise RuntimeError(
                    f"Paper пока нет для {self.mc_version}.\n"
                    f"Доступные версии: {', '.join(sorted(ids)[-10:])}"
                )
            if chosen != self.mc_version:
                self.status.emit(f"Paper для {self.mc_version} ещё нет — ставлю {chosen}.")

            url = f"{PAPER_API}/versions/{chosen}/builds"
            self.status.emit(f"Ищу последний билд Paper {chosen}...")
            r = requests.get(url, headers=PAPER_UA, timeout=30)
            if r.status_code != 200:
                raise RuntimeError(f"Paper builds API вернул {r.status_code}")
            builds = r.json()
            if isinstance(builds, dict):
                builds = builds.get("builds") or builds.get("versions") or []
            if not builds:
                raise RuntimeError(f"Нет билдов Paper {chosen}")

            chosen_build = None
            for b in builds:
                channel = (b.get("channel") or "").lower()
                if channel in ("default", "stable", "recommended") or not channel:
                    chosen_build = b
                    break
            if not chosen_build:
                chosen_build = builds[-1]

            downloads = chosen_build.get("downloads") or {}
            dl = downloads.get("server:default") or downloads.get("application") or {}
            if isinstance(dl, dict):
                dl_url = dl.get("url")
            elif isinstance(dl, str):
                dl_url = dl
            else:
                dl_url = None
            if not dl_url:
                dl_url = chosen_build.get("url")
            if not dl_url:
                raise RuntimeError("Paper API не отдал ссылку для скачивания.")

            build_num = chosen_build.get("id") or chosen_build.get("build") or "?"
            self.status.emit(f"Скачиваю Paper {chosen} build {build_num}...")

            self.dest.parent.mkdir(parents=True, exist_ok=True)
            with requests.get(dl_url, stream=True, timeout=180, headers=PAPER_UA) as resp:
                resp.raise_for_status()
                total = int(resp.headers.get("Content-Length") or 0)
                done = 0
                tmp = self.dest.with_suffix(".jar.part")
                with open(tmp, "wb") as f:
                    for chunk in resp.iter_content(1 << 16):
                        f.write(chunk)
                        done += len(chunk)
                        if total:
                            self.progress.emit(done, total)
                tmp.replace(self.dest)
            self.status.emit(f"Paper {chosen} готов")
            self.done.emit(chosen)
        except Exception as e:
            self.failed.emit(str(e))


class VanillaServerInstaller(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    done = pyqtSignal(str)
    failed = pyqtSignal(str)

    def __init__(self, mc_version: str, dest: Path):
        super().__init__()
        self.mc_version = mc_version
        self.dest = dest

    def run(self):
        try:
            self.status.emit(f"Ищу серверную версию {self.mc_version} у Mojang...")
            info = get_version_info(self.mc_version)
            server = (info.get("downloads") or {}).get("server")
            if not server:
                raise RuntimeError(f"У Mojang нет server.jar для {self.mc_version}")
            self.status.emit(f"Скачиваю server.jar ({self.mc_version})...")
            self.dest.parent.mkdir(parents=True, exist_ok=True)
            download_file(server["url"], self.dest, sha1=server.get("sha1"),
                          log=self.status.emit,
                          progress=lambda v, m: self.progress.emit(v, m))
            self.status.emit(f"server.jar {self.mc_version} готов")
            self.done.emit(self.mc_version)
        except Exception as e:
            self.failed.emit(str(e))


def _num_key(text: str) -> tuple:
    return tuple(int(x) for x in re.findall(r"\d+", text.split("-")[0]))


def _neoforge_mc(ver: str) -> str | None:
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)", ver)
    if not m:
        return None
    a, b, c = (int(x) for x in m.groups())
    if a >= 26:
        return f"{a}.{b}" if c == 0 else f"{a}.{b}.{c}"
    if a < 20:
        return None
    return f"1.{a}" if b == 0 else f"1.{a}.{b}"


def neoforge_versions_by_mc() -> dict:
    r = http_get(f"{NEOFORGE_MAVEN}/maven-metadata.xml")
    if r.status_code != 200:
        raise RuntimeError("Не могу получить список версий NeoForge")
    out: dict = {}
    for v in re.findall(r"<version>([^<]+)</version>", r.text):
        low = v.lower()
        if "alpha" in low or "snapshot" in low:
            continue
        mc = _neoforge_mc(v)
        if mc:
            out.setdefault(mc, []).append(v)
    for lst in out.values():
        lst.sort(key=_num_key)
    return out


def pick_neoforge_version(mc: str, data: dict | None = None) -> str:
    data = data if data is not None else neoforge_versions_by_mc()
    vers = data.get(mc)
    if not vers:
        raise RuntimeError(f"NeoForge для Minecraft {mc} не найден")
    stable = [v for v in vers if "-" not in v]
    return (stable or vers)[-1]


def forge_versions_by_mc() -> dict:
    r = http_get(FORGE_PROMOS_URL)
    r.raise_for_status()
    out: dict = {}
    for key, ver in (r.json().get("promos") or {}).items():
        mc, _, kind = key.rpartition("-")
        if mc and kind in ("latest", "recommended"):
            out.setdefault(mc, {})[kind] = ver
    return out


def pick_forge_version(mc: str, data: dict | None = None) -> str:
    data = data if data is not None else forge_versions_by_mc()
    ent = data.get(mc)
    if not ent:
        raise RuntimeError(f"Forge для Minecraft {mc} не найден")
    return ent.get("recommended") or ent["latest"]


def server_supported_mc(core: str) -> set | None:
    if core == "fabric":
        r = http_get(f"{FABRIC_META}/versions/game")
        r.raise_for_status()
        return {x["version"] for x in r.json()}
    if core == "forge":
        return {mc for mc in forge_versions_by_mc() if _num_key(mc) >= FORGE_SERVER_MIN_MC}
    if core == "neoforge":
        return set(neoforge_versions_by_mc())
    return None


def find_forge_like_launch(folder: Path, core: str) -> list | None:
    args_name = "win_args.txt" if os.name == "nt" else "unix_args.txt"
    group = ("net", "minecraftforge", "forge") if core == "forge" else ("net", "neoforged", "neoforge")
    base = folder.joinpath("libraries", *group)
    if base.is_dir():
        found = [p for p in base.glob(f"*/{args_name}") if p.is_file()]
        if found:
            newest = max(found, key=lambda p: p.stat().st_mtime)
            out = []
            if (folder / "user_jvm_args.txt").is_file():
                out.append("@user_jvm_args.txt")
            out += ["@" + newest.relative_to(folder).as_posix(), "nogui"]
            return out
    if core == "forge":
        jars = [j for j in folder.glob("forge-*.jar") if "installer" not in j.name.lower()]
        if jars:
            newest = max(jars, key=lambda p: p.stat().st_mtime)
            return ["-jar", newest.name, "nogui"]
    return None


class ServerVersionsWorker(QThread):
    done = pyqtSignal(str, list)
    failed = pyqtSignal(str, str)

    def __init__(self, core: str):
        super().__init__()
        self.core = core

    def run(self):
        try:
            releases = get_release_versions()
            supported = server_supported_mc(self.core)
            versions = releases if supported is None else [v for v in releases if v in supported]
            if not versions:
                raise RuntimeError(f"нет версий для {CORE_NAMES.get(self.core, self.core)}")
            self.done.emit(self.core, versions)
        except Exception as e:
            self.failed.emit(self.core, f"Не могу получить список версий: {e}")


class FabricServerInstaller(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    done = pyqtSignal(str)
    failed = pyqtSignal(str)

    def __init__(self, mc_version: str, dest: Path):
        super().__init__()
        self.mc_version = mc_version
        self.dest = dest

    def run(self):
        try:
            mc = self.mc_version
            self.status.emit(f"Ищу Fabric для Minecraft {mc}...")
            r = http_get(f"{FABRIC_META}/versions/loader/{mc}")
            r.raise_for_status()
            loaders = r.json()
            if not loaders:
                raise RuntimeError(f"Fabric пока не поддерживает Minecraft {mc}")
            pick = next((x for x in loaders if (x.get("loader") or {}).get("stable")), loaders[0])
            loader_ver = pick["loader"]["version"]

            r = http_get(f"{FABRIC_META}/versions/installer")
            r.raise_for_status()
            installers = r.json()
            if not installers:
                raise RuntimeError("Fabric Meta не отдал версию установщика")
            inst_ver = next((x["version"] for x in installers if x.get("stable")),
                            installers[0]["version"])

            url = f"{FABRIC_META}/versions/loader/{mc}/{loader_ver}/{inst_ver}/server/jar"
            self.status.emit(f"Скачиваю Fabric {loader_ver} для {mc}...")
            self.dest.parent.mkdir(parents=True, exist_ok=True)
            download_file(url, self.dest, log=self.status.emit,
                          progress=lambda v, m: self.progress.emit(v, m))
            self.status.emit(f"Fabric {loader_ver} готов")
            self.done.emit(loader_ver)
        except Exception as e:
            self.failed.emit(str(e))


class ForgeLikeServerInstaller(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    done = pyqtSignal(str)
    failed = pyqtSignal(str)

    def __init__(self, kind: str, mc_version: str, folder: Path):
        super().__init__()
        self.kind = kind
        self.mc_version = mc_version
        self.folder = folder

    def run(self):
        try:
            kind, mc = self.kind, self.mc_version
            label = CORE_NAMES.get(kind, kind)
            self.status.emit(f"Ищу {label} для Minecraft {mc}...")
            if kind == "forge":
                ver = pick_forge_version(mc)
                installer_name = f"forge-{mc}-{ver}-installer.jar"
                url = f"{FORGE_MAVEN}/{mc}-{ver}/{installer_name}"
            else:
                ver = pick_neoforge_version(mc)
                installer_name = f"neoforge-{ver}-installer.jar"
                url = f"{NEOFORGE_MAVEN}/{ver}/{installer_name}"

            java = ensure_java(mc, self.status.emit)
            RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
            installer = RUNTIME_DIR / installer_name
            self.status.emit(f"Скачиваю {installer_name}...")
            download_file(url, installer, log=self.status.emit)

            self.folder.mkdir(parents=True, exist_ok=True)
            self.status.emit(f"Ставлю {label} {ver} (это несколько минут)...")
            flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
            proc = subprocess.Popen(
                [str(java), "-jar", str(installer), "--installServer", str(self.folder)],
                cwd=str(self.folder), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace", creationflags=flags,
            )
            tail: deque = deque(maxlen=25)
            for line in proc.stdout:
                line = line.rstrip()
                if line:
                    tail.append(line)
                    self.status.emit(short_text(line, 100))
            code = proc.wait()
            if code != 0:
                raise RuntimeError(f"Установщик {label} вернул код {code}.\n\n" + "\n".join(tail))
            if find_forge_like_launch(self.folder, kind) is None:
                raise RuntimeError(
                    f"Установщик {label} завершился, но запускаемый файл не найден.\n\n"
                    + "\n".join(tail))
            self.status.emit(f"{label} {ver} готов")
            self.done.emit(ver)
        except Exception as e:
            self.failed.emit(str(e))


def ensure_eula_and_properties(srv: ServerInstance, log=print):
    srv.folder.mkdir(parents=True, exist_ok=True)

    eula = srv.folder / "eula.txt"
    if srv.eula:
        eula.write_text(
            "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\n"
            "eula=true\n",
            encoding="utf-8",
        )

    prop = srv.folder / "server.properties"
    defaults = {
        "server-port": str(srv.port),
        "online-mode": "false",
        "motd": f"Frimer - {srv.name}",
        "max-players": "20",
        "enable-command-block": "true",
        "spawn-protection": "0",
        "gamemode": "survival",
        "difficulty": "easy",
        "pvp": "true",
        "allow-flight": "true",
    }
    if not prop.exists():
        lines = ["#Minecraft server properties", "#Frimer setup"]
        for k, v in defaults.items():
            lines.append(f"{k}={v}")
        prop.write_text("\n".join(lines) + "\n", encoding="utf-8")
        log(f"Создан server.properties (port={srv.port}, online-mode=false)")
    else:
        try:
            text = prop.read_text(encoding="utf-8", errors="replace")
        except OSError:
            text = ""
        lines = text.splitlines() if text else []
        index = {}
        for i, line in enumerate(lines):
            if "=" in line and not line.lstrip().startswith("#"):
                k = line.split("=", 1)[0].strip()
                index[k] = i
        for k, v in defaults.items():
            if k in index:
                lines[index[k]] = f"{k}={v}"
            else:
                lines.append(f"{k}={v}")
        prop.write_text("\n".join(lines) + "\n", encoding="utf-8")
        log("Обновил server.properties (port, online-mode)")


class ServerRunner(QThread):
    status = pyqtSignal(str)
    log = pyqtSignal(str)
    started = pyqtSignal()
    stopped = pyqtSignal(int)
    failed = pyqtSignal(str)

    def __init__(self, srv: ServerInstance, java_exe: Path, playit_exe: Path | None = None):
        super().__init__()
        self.srv = srv
        self.java_exe = Path(java_exe)
        self.playit_exe = playit_exe
        self._proc: subprocess.Popen | None = None
        self._playit_proc: subprocess.Popen | None = None

    def stop(self):
        if self._proc and self._proc.poll() is None:
            try:
                self._proc.stdin.write("stop\n")
                self._proc.stdin.flush()
            except Exception:
                pass

    def kill(self):
        for p in (self._proc, self._playit_proc):
            if p and p.poll() is None:
                try:
                    p.terminate()
                except Exception:
                    pass

    def _pump_playit_output(self):
        proc = self._playit_proc
        if not proc or not proc.stdout:
            return
        try:
            for line in proc.stdout:
                line = line.rstrip()
                if line:
                    self.log.emit(f"[playit] {line}")
        except Exception:
            pass

    def run(self):
        try:
            launch = self.srv.launch_args()
            if launch is None:
                raise RuntimeError(f"Нет ядра сервера ({self.srv.core_label}) в {self.srv.folder}\n"
                                   f"Создай сервер заново.")
            cmd = [
                str(self.java_exe),
                "-Xmx" + str(self.srv.ram_mb) + "M",
                "-Xms" + str(self.srv.ram_mb // 2) + "M",
                *launch,
            ]
            self.status.emit("Запускаю Java-сервер...")
            flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
            self._proc = subprocess.Popen(
                cmd, cwd=str(self.srv.folder),
                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace",
                creationflags=flags, bufsize=1,
            )
            self.started.emit()
            self.status.emit("Сервер запущен")
            if self.playit_exe and self.srv.auto_playit:
                try:
                    self.log.emit(f"[playit] Запускаю {self.playit_exe}")
                    self._playit_proc = subprocess.Popen(
                        [str(self.playit_exe)], cwd=str(self.playit_exe.parent),
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                        text=True, encoding="utf-8", errors="replace",
                        creationflags=flags,
                    )
                    threading.Thread(target=self._pump_playit_output, daemon=True).start()
                except Exception as e:
                    self.log.emit(f"[playit] Не удалось запустить: {e}")

            for line in self._proc.stdout:
                self.log.emit(line.rstrip())
            code = self._proc.wait()
            self.log.emit(f"[server] Процесс завершился с кодом {code}")
            if self._playit_proc and self._playit_proc.poll() is None:
                try:
                    self._playit_proc.terminate()
                except Exception:
                    pass
            self.stopped.emit(code)
        except Exception as e:
            self.failed.emit(str(e))


# ===========================================================================
# Modpack import
# ===========================================================================
MRPACK_HOSTS = ("cdn.modrinth.com", "github.com", "raw.githubusercontent.com",
                "gitlab.com", "objects.githubusercontent.com")


def unique_instance_folder(name: str) -> Path:
    base = slugify(name)
    folder = INSTANCES_DIR / base
    n = 2
    while folder.exists():
        folder = INSTANCES_DIR / f"{base}-{n}"
        n += 1
    return folder


def _mrpack_url_ok(url: str) -> bool:
    m = re.match(r"^https://([^/:@]+)(?::\d+)?/", url or "")
    return bool(m) and m.group(1).lower() in MRPACK_HOSTS


def _mrpack_loader(deps: dict):
    if "fabric-loader" in deps:
        return "Fabric", deps["fabric-loader"]
    if "quilt-loader" in deps:
        return "Quilt", deps["quilt-loader"]
    if "neoforge" in deps:
        return "NeoForge", deps["neoforge"]
    if "forge" in deps:
        return "Forge", deps["forge"]
    return "Vanilla", None


def import_modpack(path: Path, status, progress):
    try:
        z = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError):
        raise RuntimeError("Файл повреждён или это не архив сборки.")
    with z:
        names = set(z.namelist())
        if "modrinth.index.json" not in names:
            if "manifest.json" in names:
                raise RuntimeError("Это сборка CurseForge. Пока поддерживаются только сборки Modrinth.")
            raise RuntimeError("В архиве нет modrinth.index.json — это не сборка Modrinth (.mrpack).")
        index = json.loads(z.read("modrinth.index.json").decode("utf-8-sig"))
        deps = index.get("dependencies") or {}
        mc = deps.get("minecraft")
        if not mc:
            raise RuntimeError("В сборке не указана версия Minecraft.")
        loader, loader_version = _mrpack_loader(deps)
        name = (index.get("name") or path.stem).strip() or path.stem

        folder = unique_instance_folder(name)
        inst = Instance(folder, name, loader, mc, 4096, loader_version)
        inst.save()
        for sub in ("mods", "shaderpacks", "resourcepacks", "saves", "screenshots", "crash-reports"):
            (folder / sub).mkdir(parents=True, exist_ok=True)

        warnings = []
        try:
            files = [f for f in index.get("files", [])
                     if (f.get("env") or {}).get("client") != "unsupported"]
            total = len(files)

            def fetch(f):
                dest = safe_join(folder, f["path"])
                urls = [u for u in f.get("downloads", []) if _mrpack_url_ok(u)]
                if not urls:
                    raise RuntimeError("нет разрешённой ссылки для скачивания")
                hashes = f.get("hashes") or {}
                last = None
                for url in urls:
                    try:
                        download_file(url, dest, sha1=hashes.get("sha1"),
                                      sha512=hashes.get("sha512"), log=lambda _: None)
                        return
                    except Exception as e:
                        last = e
                raise last

            status(f"Скачиваю файлы сборки ({total})...")
            progress(0, max(total, 1))
            done = 0
            with ThreadPoolExecutor(max_workers=6) as pool:
                futures = {pool.submit(fetch, f): f for f in files}
                for fut in as_completed(futures):
                    done += 1
                    try:
                        fut.result()
                    except Exception as e:
                        warnings.append(f"{futures[fut].get('path', '?')}: {e}")
                    progress(done, max(total, 1))

            status("Распаковываю настройки сборки...")
            for prefix in ("overrides/", "client-overrides/"):
                for info in z.infolist():
                    if info.is_dir() or not info.filename.startswith(prefix):
                        continue
                    rel = info.filename[len(prefix):]
                    if not rel:
                        continue
                    try:
                        dest = safe_join(folder, rel)
                    except RuntimeError:
                        warnings.append(f"пропущен небезопасный путь: {info.filename}")
                        continue
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(info) as src, open(dest, "wb") as dst:
                        shutil.copyfileobj(src, dst)
            if total and len(warnings) >= total:
                raise RuntimeError("Не удалось скачать ни одного файла сборки:\n" + "\n".join(warnings[:5]))
        except Exception:
            shutil.rmtree(folder, ignore_errors=True)
            raise
    return inst, warnings


class ImportWorker(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    done = pyqtSignal(str, str, list)
    failed = pyqtSignal(str, str)

    def __init__(self, path):
        super().__init__()
        self.path = Path(path)

    def run(self):
        try:
            inst, warns = import_modpack(self.path, self.status.emit,
                                         lambda v, m: self.progress.emit(v, m))
            self.done.emit(inst.name, str(inst.folder), warns)
        except Exception as e:
            self.failed.emit(self.path.name, str(e))


# ===========================================================================
# Workers
# ===========================================================================
class VersionsWorker(QThread):
    done = pyqtSignal(str, list)
    failed = pyqtSignal(str)

    def __init__(self, loader: str):
        super().__init__()
        self.loader = loader

    def run(self):
        try:
            releases = get_release_versions()
            self.done.emit(self.loader, releases)
        except Exception as e:
            self.failed.emit(f"Не могу получить список версий: {e}")


class LoaderVersionsWorker(QThread):
    done = pyqtSignal(str, str, list)
    failed = pyqtSignal(str)

    def __init__(self, loader: str, mc_version: str):
        super().__init__()
        self.loader = loader
        self.mc_version = mc_version

    def run(self):
        try:
            if self.loader == "Fabric":
                r = http_get(f"https://meta.fabricmc.net/v2/versions/loader/{self.mc_version}")
                r.raise_for_status()
                vers = [x["loader"]["version"] for x in r.json()]
            elif self.loader == "Quilt":
                r = http_get(f"https://meta.quiltmc.org/v3/versions/loader/{self.mc_version}")
                r.raise_for_status()
                vers = [x["loader"]["version"] for x in r.json()]
            elif self.loader == "Forge":
                vers = []
                try:
                    r = http_get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml")
                    r.raise_for_status()
                    pre = f"{self.mc_version}-"
                    for v in re.findall(r"<version>([^<]+)</version>", r.text):
                        if v.startswith(pre):
                            vers.append(v[len(pre):])
                except Exception:
                    vers = []
                if not vers:
                    r = http_get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
                    r.raise_for_status()
                    promos = r.json().get("promos", {})
                    for k, v in promos.items():
                        if k.startswith(f"{self.mc_version}-") and v not in vers:
                            vers.append(v)
            elif self.loader == "NeoForge":
                vers = list(neoforge_versions_by_mc().get(self.mc_version, []))
            else:
                vers = []
            try:
                vers.sort(key=_num_key, reverse=True)
            except Exception:
                vers.sort(reverse=True)
            self.done.emit(self.loader, self.mc_version, vers)
        except Exception as e:
            self.failed.emit(f"Не могу получить версии загрузчика: {e}")


def _loader_ver_from_dirname(loader: str, mc: str, name: str) -> str | None:
    if loader in ("Fabric", "Quilt"):
        pre, suf = f"{loader.lower()}-loader-", f"-{mc}"
        if name.startswith(pre) and name.endswith(suf) and len(name) > len(pre) + len(suf):
            return name[len(pre):-len(suf)]
    elif loader == "Forge":
        pre = f"{mc}-forge-"
        if name.startswith(pre):
            return name[len(pre):]
    elif loader == "NeoForge":
        if name.startswith("neoforge-"):
            v = name[len("neoforge-"):]
            if _neoforge_mc(v) == mc:
                return v
    return None


def installed_loader_versions(loader: str, mc: str) -> list:
    out = []
    try:
        dirs = [d for d in VERSIONS_DIR.iterdir() if d.is_dir()]
    except OSError:
        return out
    for d in dirs:
        ver = _loader_ver_from_dirname(loader, mc, d.name)
        if ver and (d / f"{d.name}.json").exists():
            out.append(ver)
    try:
        out.sort(key=_num_key, reverse=True)
    except Exception:
        out.sort(reverse=True)
    return out


class LoaderInstallWorker(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    log = pyqtSignal(str)
    done = pyqtSignal(str, str)
    failed = pyqtSignal(str)

    def __init__(self, loader: str, mc_version: str, loader_version: str | None = None):
        super().__init__()
        self.loader = loader
        self.mc_version = mc_version
        self.loader_version = loader_version

    def run(self):
        try:
            prog = lambda v, m: self.progress.emit(int(v), int(m))
            GAME_DIR.mkdir(parents=True, exist_ok=True)
            ensure_launcher_profile(GAME_DIR)
            self.status.emit(f"Готовлю Minecraft {self.mc_version}...")
            VanillaInstaller(log_cb=self.log.emit, progress_cb=prog,
                             version_id=self.mc_version).install()
            path = None
            if self.loader in ("Fabric", "Quilt"):
                self.status.emit(f"Ставлю {self.loader}...")
                path = install_fabric_like(self.loader.lower(), self.mc_version,
                                           self.loader_version, self.log.emit, prog)
            elif self.loader in ("Forge", "NeoForge"):
                self.status.emit(f"Ставлю {self.loader}...")
                path = install_forge_like(self.loader.lower(), self.mc_version,
                                          self.loader_version, self.log.emit, prog)
            ver = ""
            if path is not None:
                ver = (_loader_ver_from_dirname(self.loader, self.mc_version, Path(path).parent.name)
                       or self.loader_version or "")
            self.done.emit(self.loader, ver)
        except Exception as e:
            self.failed.emit(str(e))


def _clip(text: str, limit: int = 120) -> str:
    text = " ".join((text or "").split())
    while len(text.encode("utf-8")) > limit:
        text = text[:-1]
    return text


def _dispose_rpc(rpc):
    if rpc is None:
        return
    for action in (lambda: rpc.clear(), lambda: rpc.close()):
        try:
            action()
        except Exception:
            pass
    try:
        loop = getattr(rpc, "loop", None)
        if loop is not None and not loop.is_closed():
            loop.close()
    except Exception:
        pass


class DiscordPresence(QThread):
    def __init__(self, client_id: str):
        super().__init__()
        self.client_id = (client_id or "").strip()
        self._lock = threading.Lock()
        self._wake = threading.Event()
        self._state = None
        self._stopping = False

    def set_presence(self, **presence):
        clean = {k: (_clip(v) if isinstance(v, str) else v)
                 for k, v in presence.items() if v}
        with self._lock:
            self._state = clean or None
        self._wake.set()

    def stop(self):
        self._stopping = True
        self._wake.set()

    def run(self):
        if Presence is None or not self.client_id:
            return
        rpc = None
        sent = object()
        try:
            while not self._stopping:
                self._wake.clear()
                if rpc is None:
                    candidate = None
                    try:
                        candidate = Presence(self.client_id)
                        candidate.connect()
                        rpc = candidate
                        sent = object()
                    except Exception:
                        _dispose_rpc(candidate)
                        self._wake.wait(20)
                        continue
                with self._lock:
                    state = self._state
                if state != sent:
                    try:
                        if state:
                            rpc.update(**state)
                        else:
                            rpc.clear()
                        sent = state
                    except Exception:
                        _dispose_rpc(rpc)
                        rpc = None
                        continue
                if not self._wake.wait(60):
                    sent = object()
        finally:
            _dispose_rpc(rpc)


# ===========================================================================
# Crash analysis
# ===========================================================================
CRASH_HINTS = [
    (r"OutOfMemoryError|Could not reserve enough space|GC overhead limit",
     "Не хватает оперативной памяти. Увеличь RAM у сборки (правый клик - Изменить)."),
    (r"UnsupportedClassVersionError|compiled by a more recent version of the Java Runtime",
     "Неподходящая версия Java для этой версии Minecraft или загрузчика."),
    (r"Module [\w.\-]+ not found|java\.lang\.module\.FindException|ResolutionException",
     "Загрузчик не нашёл нужную библиотеку. Удали папку этой версии в .mylauncher\\versions."),
    (r"Mixin apply failed|MixinApplyError|InvalidMixinException|Mixin transformation",
     "Конфликт модов (Mixin). Отключи недавно добавленные моды."),
    (r"Incompatible mods found|Missing or unsupported mandatory dependencies|Unmet dependency|"
     r"requires any version|requires version|Could not find required mod",
     "У какого-то мода не хватает обязательной зависимости."),
    (r"NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|ClassNotFoundException",
     "Какой-то мод собран под другую версию игры или загрузчика."),
    (r"EXCEPTION_ACCESS_VIOLATION|A fatal error has been detected|SIGSEGV",
     "Сбой на уровне Java или видеодрайвера."),
    (r"GLFW error|Pixel format not accelerated|failed to create window|OpenGL error",
     "Проблема с графикой. Обнови драйвер видеокарты."),
]


def _find_crash_report(inst_dir: Path, started_at: float):
    candidates = []
    try:
        candidates += list((inst_dir / "crash-reports").glob("crash-*.txt"))
        candidates += list(inst_dir.glob("hs_err_pid*.log"))
        candidates = [f for f in candidates if f.stat().st_mtime >= (started_at or 0) - 5]
    except OSError:
        return None
    return max(candidates, key=lambda f: f.stat().st_mtime) if candidates else None


def _crash_cause(text: str) -> str:
    desc = re.search(r"^Description:\s*(.+)$", text, re.M)
    caused = re.findall(r"^Caused by:\s*(.+)$", text, re.M)
    exc = re.findall(r"^((?:[\w$]+\.)+[\w$]*(?:Exception|Error)\b.*)$", text, re.M)
    parts = []
    if desc:
        parts.append(desc.group(1).strip())
    if caused:
        parts.append(caused[-1].strip())
    elif exc:
        parts.append(exc[0].strip())
    return short_text(" - ".join(parts), 300)


def analyze_crash(code: int, tail_lines: list, inst_dir: Path, started_at: float,
                  loader: str, mc_version: str):
    crash_file = _find_crash_report(inst_dir, started_at)
    report = ""
    if crash_file:
        try:
            report = crash_file.read_text(encoding="utf-8", errors="replace")
        except OSError:
            report = ""
    tail = "\n".join(tail_lines)
    scan = report + "\n" + tail

    lines = [f"Игра ({loader} {mc_version}) завершилась с ошибкой, код {code}."]
    cause = _crash_cause(scan)
    if cause:
        lines.append("Причина: " + cause)
    hints = [msg for pat, msg in CRASH_HINTS if re.search(pat, scan, re.M)]
    for h in hints[:2]:
        lines.append("Подсказка: " + h)
    if not hints:
        lines.append("Точная причина не определена, подробности в тексте ниже.")
    if crash_file:
        lines.append(f"Краш-репорт: {crash_file.name}")

    body = report if report.strip() else tail
    return "\n".join(lines), body[-80000:]


class LaunchWorker(QThread):
    status = pyqtSignal(str)
    progress = pyqtSignal(int, int)
    log = pyqtSignal(str)
    failed = pyqtSignal(str)
    finished_game = pyqtSignal(int)
    game_started = pyqtSignal(int)
    crashed = pyqtSignal(str, str, str)
    perf_ready = pyqtSignal(dict)

    def __init__(self, loader, mc_version, nickname, ram_mb, loader_version=None, inst_dir=None):
        super().__init__()
        self.loader = loader
        self.mc_version = mc_version
        self.nickname = nickname
        self.ram_mb = ram_mb
        self.loader_version = loader_version
        self.inst_dir = Path(inst_dir) if inst_dir else None
        self._started_at = None
        self._proc = None
        self._killed = False

    def run(self):
        try:
            self._run()
        except Exception as e:
            self.failed.emit(str(e))

    def kill_game(self) -> bool:
        proc = self._proc
        if proc is None or proc.poll() is not None:
            return False
        self._killed = True
        try:
            if os.name == "nt":
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(proc.pid)],
                               capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW)
            else:
                proc.kill()
        except Exception:
            pass
        if proc.poll() is None:
            try:
                proc.kill()
            except Exception:
                pass
        return True

    def _run(self):
        GAME_DIR.mkdir(parents=True, exist_ok=True)
        ensure_launcher_profile(GAME_DIR)

        self.status.emit(f"Готовлю {self.loader} {self.mc_version}...")
        vanilla_json = VanillaInstaller(
            log_cb=self.log.emit,
            progress_cb=lambda v, m: self.progress.emit(v, m),
            version_id=self.mc_version,
        ).install()

        if self.loader == "Vanilla":
            version_json = vanilla_json
        elif self.loader == "Fabric":
            self.status.emit("Ставлю Fabric...")
            version_json = install_fabric_like(
                "fabric", self.mc_version, self.loader_version, self.log.emit,
                lambda v, m: self.progress.emit(v, m))
        elif self.loader == "Quilt":
            self.status.emit("Ставлю Quilt...")
            version_json = install_fabric_like(
                "quilt", self.mc_version, self.loader_version, self.log.emit,
                lambda v, m: self.progress.emit(v, m))
        elif self.loader == "Forge":
            self.status.emit("Ставлю Forge...")
            version_json = install_forge_like(
                "forge", self.mc_version, self.loader_version, self.log.emit,
                lambda v, m: self.progress.emit(v, m))
        elif self.loader == "NeoForge":
            self.status.emit("Ставлю NeoForge...")
            version_json = install_forge_like(
                "neoforge", self.mc_version, self.loader_version, self.log.emit,
                lambda v, m: self.progress.emit(v, m))
        else:
            version_json = vanilla_json

        self.status.emit("Готовлю Java...")
        java = ensure_java(self.mc_version, self.log.emit,
                           lambda d, t: self.progress.emit(d // 1024, t // 1024))

        inst_dir = self.inst_dir or (INSTANCES_DIR / f"{slugify(self.loader)}-{self.mc_version}")
        inst_dir.mkdir(parents=True, exist_ok=True)
        for sub in ("mods", "resourcepacks", "shaderpacks", "saves", "screenshots", "crash-reports"):
            (inst_dir / sub).mkdir(exist_ok=True)

        cmd = build_launch_command(version_json, inst_dir, java, self.nickname, self.ram_mb)
        self.log.emit("Запускаю игру...")
        self.status.emit("Игра запущена")

        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        try:
            mods_snapshot = snapshot_mods(inst_dir / "mods")
        except Exception:
            mods_snapshot = {}
        proc = subprocess.Popen(cmd, cwd=str(inst_dir), stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True,
                                encoding="utf-8", errors="replace", creationflags=flags)
        self._proc = proc
        self._started_at = time.time()
        perf = PerfRecorder(self.ram_mb, mods_snapshot)
        sampler = PerfSampler(proc.pid)
        sampler.start()
        self.game_started.emit(int(self._started_at))

        tail = deque(maxlen=400)
        for line in proc.stdout:
            line = line.rstrip()
            tail.append(line)
            perf.feed(line)
            self.log.emit(line)
        code = proc.wait()
        sampler.stop()
        self._proc = None
        if self._killed:
            self.log.emit("Игра закрыта принудительно")
        else:
            self.log.emit(f"Игра завершилась с кодом {code}")
        if self._started_at:
            self.finished_game.emit(int(time.time() - self._started_at))
        try:
            session = perf.finish(code, self._killed, sampler)
            if session is not None:
                save_perf_session(inst_dir, session)
                self.perf_ready.emit(session)
        except Exception:
            pass
        if code != 0 and not self._killed:
            try:
                summary, body = analyze_crash(code, list(tail), inst_dir, self._started_at,
                                              self.loader, self.mc_version)
                self.crashed.emit(summary, body, str(inst_dir / "crash-reports"))
            except Exception:
                pass


# ===========================================================================
# Modrinth / CurseForge
# ===========================================================================
class ApiKeyError(RuntimeError):
    pass


class ManualDownloadError(RuntimeError):
    def __init__(self, msg, url):
        super().__init__(msg)
        self.url = url


class ModrinthProvider:
    name = "Modrinth"
    API = "https://api.modrinth.com/v2"
    PROJECT_TYPE = "mod"
    EXTRA_FACETS: list = []

    def search(self, query, mc, loader, offset, limit=20):
        facets = [[f"project_type:{self.PROJECT_TYPE}"], [f"versions:{mc}"]]
        if self.PROJECT_TYPE in ("mod", "plugin"):
            facets.append([f"categories:{loader.lower()}"])
        facets.extend(self.EXTRA_FACETS)
        params = {"query": query, "facets": json.dumps(facets), "limit": limit,
                  "offset": offset, "index": "relevance" if query else "downloads"}
        r = http_get(f"{self.API}/search", params=params)
        if r.status_code != 200:
            raise RuntimeError(f"Modrinth: ошибка {r.status_code}")
        data = r.json()
        mods = [{"id": h["project_id"], "title": h["title"], "author": h.get("author", ""),
                 "description": h.get("description", ""), "downloads": h.get("downloads", 0),
                 "icon_url": h.get("icon_url"),
                 "page_url": f"https://modrinth.com/{self.PROJECT_TYPE}/{h['slug']}",
                 "slug": h.get("slug", "")} for h in data["hits"]]
        return mods, data.get("total_hits", 0)

    def list_versions(self, mod, mc, loader):
        params = {"game_versions": json.dumps([mc])}
        if self.PROJECT_TYPE in ("mod", "plugin"):
            params["loaders"] = json.dumps([loader.lower()])
        r = http_get(f"{self.API}/project/{mod['id']}/version", params=params)
        if r.status_code != 200:
            raise RuntimeError(f"Modrinth: ошибка {r.status_code}")
        out = []
        for v in r.json():
            if not v.get("files"):
                continue
            out.append({"id": v["id"],
                        "label": v.get("version_number") or v.get("name") or v["id"],
                        "type": v.get("version_type", "release"),
                        "date": (v.get("date_published") or "")[:10],
                        "mc": v.get("game_versions") or []})
        return out

    def install(self, mod, mc, loader, dest, version=None):
        files, warns, seen = [], [], set()
        self._install(mod["id"], mc, loader, dest, files, warns, seen, version)
        return files, warns

    def _install(self, pid, mc, loader, dest, files, warns, seen, pick=None):
        if pid in seen:
            return
        seen.add(pid)
        params = {"game_versions": json.dumps([mc])}
        if self.PROJECT_TYPE in ("mod", "plugin"):
            params["loaders"] = json.dumps([loader.lower()])
        r = http_get(f"{self.API}/project/{pid}/version", params=params)
        if r.status_code != 200:
            raise RuntimeError(f"Modrinth: ошибка {r.status_code}")
        versions = r.json()
        if pick:
            version = next((v for v in versions if v.get("id") == pick["id"]), None)
            if version is None:
                rv = http_get(f"{self.API}/version/{pick['id']}")
                if rv.status_code != 200:
                    raise RuntimeError(f"Modrinth: ошибка {rv.status_code}")
                version = rv.json()
        else:
            if not versions:
                raise RuntimeError(f"Нет версии для {loader} {mc}")
            version = next((v for v in versions if v.get("version_type") == "release"), versions[0])
        vfiles = version.get("files") or []
        if not vfiles:
            raise RuntimeError("У версии нет файлов")
        f = next((x for x in vfiles if x.get("primary")), vfiles[0])
        hashes = f.get("hashes") or {}
        out = dest / f["filename"]
        download_file(f["url"], out, sha1=hashes.get("sha1"),
                      sha512=hashes.get("sha512"), log=lambda _: None)
        files.append(f["filename"])
        if self.PROJECT_TYPE in ("mod", "plugin"):
            for dep in version.get("dependencies", []):
                if dep.get("dependency_type") == "required" and dep.get("project_id"):
                    try:
                        self._install(dep["project_id"], mc, loader, dest, files, warns, seen)
                    except RuntimeError as e:
                        warns.append(f"dependency {dep['project_id']}: {e}")


class ModrinthServerModProvider(ModrinthProvider):
    name = "Modrinth (серверные моды)"
    EXTRA_FACETS = [["server_side:required", "server_side:optional", "server_side:unknown"]]


class ModrinthShaderProvider(ModrinthProvider):
    name = "Modrinth (shaders)"
    PROJECT_TYPE = "shader"


class ModrinthResourcepackProvider(ModrinthProvider):
    name = "Modrinth (resourcepacks)"
    PROJECT_TYPE = "resourcepack"


class ModrinthDatapackProvider(ModrinthProvider):
    name = "Modrinth (datapacks)"
    PROJECT_TYPE = "datapack"


class ModrinthPluginProvider(ModrinthProvider):
    name = "Modrinth (плагины)"
    PROJECT_TYPE = "plugin"


class CurseForgeProvider:
    name = "CurseForge"
    API = "https://api.curseforge.com/v1"
    LOADER = {"Forge": 1, "Fabric": 4, "Quilt": 5, "NeoForge": 6}

    def __init__(self, key):
        self.headers = {**UA, "x-api-key": key, "Accept": "application/json"}

    def _get(self, path, **params):
        r = http_get(self.API + path, params=params, headers=self.headers)
        if r.status_code in (401, 403):
            raise ApiKeyError("CurseForge отклонил API-ключ")
        if r.status_code != 200:
            raise RuntimeError(f"CurseForge: ошибка {r.status_code}")
        return r.json()

    def search(self, query, mc, loader, offset, limit=20):
        if loader not in self.LOADER:
            raise RuntimeError(f"CurseForge не поддерживает {loader}")
        params = {"gameId": 432, "classId": 6, "gameVersion": mc, "pageSize": limit,
                  "index": offset, "sortField": 1 if query else 2, "sortOrder": "desc",
                  "modLoaderType": self.LOADER[loader]}
        if query:
            params["searchFilter"] = query
        data = self._get("/mods/search", **params)
        mods = []
        for m in data["data"]:
            authors = m.get("authors") or []
            mods.append({"id": m["id"], "title": m["name"],
                         "author": authors[0]["name"] if authors else "",
                         "description": m.get("summary", ""),
                         "downloads": int(m.get("downloadCount", 0)),
                         "icon_url": (m.get("logo") or {}).get("thumbnailUrl"),
                         "page_url": (m.get("links") or {}).get("websiteUrl", ""),
                         "slug": m.get("slug", ""), "raw": m})
        return mods, data["pagination"]["totalCount"]

    def list_versions(self, mod, mc, loader):
        params = {"gameVersion": mc, "pageSize": 50}
        lt = self.LOADER.get(loader)
        if lt:
            params["modLoaderType"] = lt
        data = self._get(f"/mods/{mod['id']}/files", **params)["data"]
        kinds = {1: "release", 2: "beta", 3: "alpha"}
        out = []
        for f in data:
            out.append({"id": f["id"],
                        "label": f.get("displayName") or f.get("fileName") or str(f["id"]),
                        "type": kinds.get(f.get("releaseType"), "release"),
                        "date": (f.get("fileDate") or "")[:10],
                        "mc": [g for g in (f.get("gameVersions") or []) if g[:1].isdigit()],
                        "raw": f})
        out.sort(key=lambda v: v["date"], reverse=True)
        return out

    def install(self, mod, mc, loader, dest, version=None):
        files, warns, seen = [], [], set()
        self._install_raw(mod["raw"], mc, loader, dest, files, warns, seen, version)
        return files, warns

    def _install_raw(self, raw, mc, loader, dest, files, warns, seen, pick=None):
        mod_id = raw["id"]
        if mod_id in seen:
            return
        seen.add(mod_id)
        if pick is not None:
            info = pick["raw"]
        else:
            lt = self.LOADER.get(loader)
            variants = [i for i in raw.get("latestFilesIndexes", [])
                        if i.get("gameVersion") == mc and (lt is None or i.get("modLoader") in (lt, None))]
            if not variants:
                raise RuntimeError(f"Нет версии для {loader} {mc}")
            variants.sort(key=lambda i: i.get("releaseType", 3))
            info = self._get(f"/mods/{mod_id}/files/{variants[0]['fileId']}")["data"]
        url = info.get("downloadUrl")
        if not url:
            page = (raw.get("links") or {}).get("websiteUrl", "https://www.curseforge.com")
            raise ManualDownloadError("Автор запретил скачивание третьими лицами.", page)
        download_file(url, dest / info["fileName"], log=lambda _: None)
        files.append(info["fileName"])
        for dep in info.get("dependencies", []):
            if dep.get("relationType") == 3:
                try:
                    dep_raw = self._get(f"/mods/{dep['modId']}")["data"]
                    self._install_raw(dep_raw, mc, loader, dest, files, warns, seen)
                except RuntimeError as e:
                    warns.append(f"dependency {dep['modId']}: {e}")


class CurseForgeDatapackProvider(CurseForgeProvider):
    name = "CurseForge (datapacks)"
    CLASS_ID = 6945

    def search(self, query, mc, loader, offset, limit=20):
        params = {"gameId": 432, "classId": self.CLASS_ID, "gameVersion": mc, "pageSize": limit,
                  "index": offset, "sortField": 1 if query else 2, "sortOrder": "desc"}
        if query:
            params["searchFilter"] = query
        data = self._get("/mods/search", **params)
        mods = []
        for m in data["data"]:
            authors = m.get("authors") or []
            mods.append({"id": m["id"], "title": m["name"],
                         "author": authors[0]["name"] if authors else "",
                         "description": m.get("summary", ""),
                         "downloads": int(m.get("downloadCount", 0)),
                         "icon_url": (m.get("logo") or {}).get("thumbnailUrl"),
                         "page_url": (m.get("links") or {}).get("websiteUrl", ""),
                         "slug": m.get("slug", ""), "raw": m})
        return mods, data["pagination"]["totalCount"]

    def install(self, mod, mc, loader, dest, version=None):
        files, warns, seen = [], [], set()
        self._install_raw(mod["raw"], mc, loader, dest, files, warns, seen, version)
        return files, warns

    def _install_raw(self, raw, mc, loader, dest, files, warns, seen, pick=None):
        mod_id = raw["id"]
        if mod_id in seen:
            return
        seen.add(mod_id)
        if pick is not None:
            info = pick["raw"]
        else:
            variants = [i for i in raw.get("latestFilesIndexes", []) if i.get("gameVersion") == mc]
            if not variants:
                raise RuntimeError(f"Нет версии для Minecraft {mc}")
            variants.sort(key=lambda i: i.get("releaseType", 3))
            info = self._get(f"/mods/{mod_id}/files/{variants[0]['fileId']}")["data"]
        url = info.get("downloadUrl")
        if not url:
            page = (raw.get("links") or {}).get("websiteUrl", "https://www.curseforge.com")
            raise ManualDownloadError("Автор запретил скачивание третьими лицами.", page)
        download_file(url, dest / info["fileName"], log=lambda _: None)
        files.append(info["fileName"])


# ===========================================================================
# ModBrowser
# ===========================================================================
class SearchWorker(QThread):
    done = pyqtSignal(int, list, int)
    failed = pyqtSignal(int, str, bool)

    def __init__(self, provider, query, mc, loader, offset, generation):
        super().__init__()
        self.provider, self.query, self.mc = provider, query, mc
        self.loader, self.offset, self.generation = loader, offset, generation

    def run(self):
        try:
            mods, total = self.provider.search(self.query, self.mc, self.loader, self.offset)
            self.done.emit(self.generation, mods, total)
        except ApiKeyError as e:
            self.failed.emit(self.generation, str(e), True)
        except Exception as e:
            self.failed.emit(self.generation, str(e), False)


class IconsWorker(QThread):
    icon = pyqtSignal(int, int, bytes)

    def __init__(self, generation, items):
        super().__init__()
        self.generation = generation
        self.items = items
        self._cancelled = False

    def cancel(self):
        self._cancelled = True

    def _fetch(self, item):
        if self._cancelled:
            return
        idx, url = item
        try:
            r = requests.get(url, timeout=10, headers=UA)
            if r.status_code == 200 and not self._cancelled:
                self.icon.emit(self.generation, idx, r.content)
        except requests.RequestException:
            pass

    def run(self):
        with ThreadPoolExecutor(max_workers=6) as pool:
            list(pool.map(self._fetch, self.items))


def remove_old_mod_files(dest: Path, title: str, keep: set) -> list:
    removed = []
    if not title:
        return removed
    try:
        for f in list(dest.iterdir()):
            if not f.is_file() or f.name in keep:
                continue
            base = f.name[:-9] if f.name.endswith(".disabled") else f.name
            if load_install_meta(dest, base)[0] == title:
                f.unlink()
                delete_install_meta(dest, base)
                removed.append(f.name)
    except OSError:
        pass
    return removed


class ModVersionsWorker(QThread):
    done = pyqtSignal(list)
    failed = pyqtSignal(str)

    def __init__(self, provider, mod, mc, loader):
        super().__init__()
        self.provider, self.mod, self.mc, self.loader = provider, mod, mc, loader

    def run(self):
        try:
            self.done.emit(self.provider.list_versions(self.mod, self.mc, self.loader))
        except Exception as e:
            self.failed.emit(str(e))


class InstallWorker(QThread):
    done = pyqtSignal(object, list, list)
    failed = pyqtSignal(object, str)
    manual = pyqtSignal(object, str, str)

    def __init__(self, provider, mod, mc, loader, dest, button, version=None):
        super().__init__()
        self.provider, self.mod, self.mc = provider, mod, mc
        self.loader, self.dest, self.button = loader, dest, button
        self.version = version

    def run(self):
        try:
            files, warns = self.provider.install(self.mod, self.mc, self.loader, self.dest,
                                                 self.version)
            if files:
                if self.version is not None:
                    removed = remove_old_mod_files(self.dest, self.mod.get("title", ""), set(files))
                    if removed:
                        warns = list(warns) + [f"заменил старую версию: {', '.join(removed)}"]
                save_install_meta(self.dest, files[0], self.mod.get("title", ""),
                                  self.mod.get("icon_url"))
            self.done.emit(self.button, files, warns)
        except ManualDownloadError as e:
            self.manual.emit(self.button, e.url, str(e))
        except Exception as e:
            self.failed.emit(self.button, str(e))


# ===========================================================================
# Диалоги: версии, браузер модов
# ===========================================================================
class ModVersionDialog(QDialog):
    KIND_RU = {"release": "релиз", "beta": "бета", "alpha": "альфа"}

    def __init__(self, parent, mod, versions: list, mc: str, loader: str, replaces: bool = False):
        super().__init__(parent)
        self.setWindowTitle(f"Версии: {mod['title']}")
        self.resize(520, 480)
        self.versions = versions
        self._chosen = None

        head = QLabel(mod["title"])
        head.setObjectName("status")
        sub = QLabel(f"{loader}, Minecraft {mc} — версий: {len(versions)}")
        sub.setObjectName("subtitle")

        self.only_release = QCheckBox("Только релизы")
        self.only_release.toggled.connect(self._fill)

        self.list = QListWidget()
        self.list.itemDoubleClicked.connect(lambda _i: self._accept())

        note = QLabel("Уже установлено: прежний файл этого мода будет заменён выбранной версией."
                      if replaces else "")
        note.setObjectName("subtitle")
        note.setWordWrap(True)
        note.setVisible(replaces)

        self.ok = QPushButton("Скачать эту версию")
        self.ok.setObjectName("install")
        self.ok.clicked.connect(self._accept)
        cancel = QPushButton("Отмена")
        cancel.setObjectName("toolsbtn")
        cancel.clicked.connect(self.reject)
        row = QHBoxLayout()
        row.addWidget(self.only_release)
        row.addStretch(1)
        row.addWidget(cancel)
        row.addWidget(self.ok)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 14)
        layout.setSpacing(8)
        layout.addWidget(head)
        layout.addWidget(sub)
        layout.addWidget(self.list, 1)
        layout.addWidget(note)
        layout.addLayout(row)
        self._fill()

    def _fill(self):
        self.list.clear()
        only = self.only_release.isChecked()
        for v in self.versions:
            if only and v.get("type") != "release":
                continue
            mcs = v.get("mc") or []
            tail = ", ".join(mcs[:3]) + (" …" if len(mcs) > 3 else "")
            line2 = " · ".join(x for x in (self.KIND_RU.get(v.get("type"), v.get("type", "")),
                                            v.get("date", ""), tail) if x)
            item = QListWidgetItem(f"{v['label']}\n{line2}")
            item.setData(Qt.ItemDataRole.UserRole, v)
            self.list.addItem(item)
        if self.list.count():
            self.list.setCurrentRow(0)
        self.ok.setEnabled(self.list.count() > 0)

    def _accept(self):
        item = self.list.currentItem()
        if item:
            self._chosen = item.data(Qt.ItemDataRole.UserRole)
            self.accept()

    def selected(self):
        return self._chosen


class ModBrowser(QDialog):
    def __init__(self, parent, provider, loader, mc, dest_dir, settings):
        super().__init__(parent)
        self.setObjectName("modbrowser")
        self.setWindowTitle(provider.name)
        self.resize(780, 700)
        self.setMinimumSize(620, 520)
        self.provider, self.loader, self.mc = provider, loader, mc
        self.dest_dir, self.settings = dest_dir, settings
        self.query, self.offset, self.total, self.generation = "", 0, 0, 0
        self.icon_labels, self.icon_workers, self.installed_count = [], [], 0

        self.search = QLineEdit()
        self.search.setPlaceholderText("Поиск, например sodium или jei")
        info = QLabel(f"{provider.name}: {loader}, Minecraft {mc}")
        info.setObjectName("subtitle")

        self.list = QListWidget()
        self.list.setObjectName("modlist")
        self.list.setSpacing(8)
        self.list.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.list.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        self.more_btn = QPushButton("Показать ещё")
        self.more_btn.setObjectName("folder")
        self.more_btn.setVisible(False)

        self.folder_btn = QPushButton("Открыть папку")
        self.folder_btn.setObjectName("folder")
        self.folder_btn.clicked.connect(self.open_folder)

        self.status = QLabel("")
        self.status.setObjectName("subtitle")
        self.status.setWordWrap(True)
        self.counter = QLabel("Установлено за сессию: 0")
        self.counter.setObjectName("counter")

        bottom = QHBoxLayout()
        bottom.addWidget(self.counter, 1)
        bottom.addWidget(self.folder_btn, 0)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 16)
        layout.setSpacing(10)
        layout.addWidget(self.search)
        layout.addWidget(info)
        layout.addWidget(self.list, 1)
        layout.addWidget(self.more_btn)
        layout.addWidget(self.status)
        layout.addLayout(bottom)

        self.timer = QTimer(self)
        self.timer.setSingleShot(True)
        self.timer.setInterval(450)
        self.timer.timeout.connect(self.new_search)
        self.search.textChanged.connect(lambda _: self.timer.start())
        self.search.returnPressed.connect(self.new_search)
        self.more_btn.clicked.connect(self.load_page)
        self.search.setFocus()
        self.new_search()

    def open_folder(self):
        self.dest_dir.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.dest_dir)))

    def _cancel_icons(self):
        for w in self.icon_workers:
            w.cancel()
        self.icon_workers = []

    def new_search(self):
        self.timer.stop()
        self.query = self.search.text().strip()
        self.offset = 0
        self.generation += 1
        self._cancel_icons()
        self.icon_labels = []
        self.list.clear()
        self.load_page()

    def load_page(self):
        self.status.setText("Загрузка...")
        self.more_btn.setVisible(False)
        w = SearchWorker(self.provider, self.query, self.mc, self.loader, self.offset, self.generation)
        w.done.connect(self.on_results)
        w.failed.connect(self.on_failed)
        spawn(w)

    def on_results(self, generation, mods, total):
        if generation != self.generation:
            return
        start = len(self.icon_labels)
        for mod in mods:
            self.add_row(mod)
        self.offset += len(mods)
        self.total = total
        if not mods and start == 0:
            self.status.setText("Ничего не найдено.")
        elif self.query:
            self.status.setText(f"Найдено: {total}")
        else:
            self.status.setText(f"Всего доступно: {total}")
        self.more_btn.setVisible(bool(mods) and self.offset < self.total)
        items = [(start + i, m["icon_url"]) for i, m in enumerate(mods) if m.get("icon_url")]
        if items:
            iw = IconsWorker(generation, items)
            iw.icon.connect(self.on_icon)
            self.icon_workers.append(iw)
            spawn(iw)

    def on_failed(self, generation, message, bad_key):
        if generation != self.generation:
            return
        self.status.setText(message)
        self.more_btn.setVisible(self.list.count() > 0)
        if bad_key:
            self.settings.setValue("curseforge_key", "")
            QMessageBox.warning(self, "Ключ отклонён", "CurseForge отклонил API-ключ.")

    def add_row(self, mod):
        row = QFrame()
        row.setObjectName("modrow")
        row.setFixedHeight(100)
        icon = QLabel()
        icon.setObjectName("modicon")
        icon.setFixedSize(56, 56)
        icon.setAlignment(Qt.AlignmentFlag.AlignCenter)
        title = QLabel(mod["title"])
        title.setObjectName("modtitle")
        title.setMinimumWidth(1)
        by = f"от {mod['author']}, " if mod["author"] else ""
        meta = QLabel(f"{by}скачиваний: {fmt_num(mod['downloads'])}")
        meta.setObjectName("modmeta")
        meta.setMinimumWidth(1)
        desc = QLabel(short_text(mod["description"]))
        desc.setObjectName("moddesc")
        desc.setWordWrap(True)
        text = QVBoxLayout()
        text.setSpacing(2)
        text.addWidget(title)
        text.addWidget(meta)
        text.addWidget(desc)
        text.addStretch(1)
        already = self.is_installed(mod)
        btn = QPushButton()
        btn.setFixedWidth(130)
        if already:
            btn.setText("Установлено")
            btn.setObjectName("installed")
            btn.setEnabled(False)
        else:
            btn.setText("Скачать")
            btn.setObjectName("install")
            btn.clicked.connect(lambda _=False, m=mod, b=btn: self.install(m, b))
        vbtn = QPushButton("Версия…")
        vbtn.setObjectName("toolsbtn")
        vbtn.setFixedWidth(130)
        vbtn.clicked.connect(lambda _=False, m=mod, b=btn, vb=vbtn: self.pick_version(m, b, vb))
        buttons = QVBoxLayout()
        buttons.setSpacing(6)
        buttons.addStretch(1)
        buttons.addWidget(btn)
        buttons.addWidget(vbtn)
        buttons.addStretch(1)
        h = QHBoxLayout(row)
        h.setContentsMargins(12, 10, 14, 10)
        h.setSpacing(12)
        h.addWidget(icon, 0, Qt.AlignmentFlag.AlignTop)
        h.addLayout(text, 1)
        h.addLayout(buttons, 0)
        item = QListWidgetItem()
        item.setSizeHint(QSize(0, 100))
        self.list.addItem(item)
        self.list.setItemWidget(item, row)
        self.icon_labels.append(icon)

    def is_installed(self, mod) -> bool:
        needles = set()
        if mod.get("slug"):
            needles.add(mod["slug"].lower())
        if mod.get("title"):
            needles.add(mod["title"].lower().replace(" ", ""))
        try:
            for f in self.dest_dir.iterdir():
                if not f.is_file():
                    continue
                flat = f.stem.lower().replace(" ", "").replace("-", "").replace("_", "")
                for n in needles:
                    if n and n in flat:
                        return True
        except OSError:
            pass
        return False

    def on_icon(self, generation, index, data):
        if generation != self.generation or index >= len(self.icon_labels):
            return
        pixmap = QPixmap()
        if pixmap.loadFromData(data):
            self.icon_labels[index].setPixmap(pixmap.scaled(
                56, 56, Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation))

    def pick_version(self, mod, btn, vbtn):
        vbtn.setEnabled(False)
        vbtn.setText("Загрузка...")
        self.status.setText(f"Загружаю список версий {mod['title']}...")
        w = ModVersionsWorker(self.provider, mod, self.mc, self.loader)
        w.done.connect(lambda versions, m=mod, b=btn, vb=vbtn: self._on_versions(m, b, vb, versions))
        w.failed.connect(lambda msg, vb=vbtn: self._on_versions_failed(vb, msg))
        spawn(w)

    def _reset_vbtn(self, vbtn):
        if not sip.isdeleted(vbtn):
            vbtn.setEnabled(True)
            vbtn.setText("Версия…")

    def _on_versions_failed(self, vbtn, message):
        self._reset_vbtn(vbtn)
        self.status.setText(f"Не удалось получить версии: {message}")

    def _on_versions(self, mod, btn, vbtn, versions):
        self._reset_vbtn(vbtn)
        if not versions:
            self.status.setText(f"Нет версий для {self.loader} {self.mc}.")
            return
        self.status.setText("")
        replaces = (not sip.isdeleted(btn)) and btn.text() == "Установлено"
        dlg = ModVersionDialog(self, mod, versions, self.mc, self.loader, replaces)
        if dlg.exec() != QDialog.DialogCode.Accepted or not dlg.selected():
            return
        if sip.isdeleted(btn):
            return
        self.install(mod, btn, dlg.selected())

    def install(self, mod, btn, version=None):
        btn.setProperty("prev_text", btn.text())
        btn.setEnabled(False)
        btn.setText("Скачивание...")
        label = f" {version['label']}" if version else ""
        self.status.setText(f"Скачиваю {mod['title']}{label}...")
        w = InstallWorker(self.provider, mod, self.mc, self.loader, self.dest_dir, btn, version)
        w.done.connect(self.on_installed)
        w.failed.connect(self.on_install_failed)
        w.manual.connect(self.on_manual)
        spawn(w)

    def _restore_btn(self, btn):
        if btn.property("prev_text") == "Установлено":
            btn.setText("Установлено")
            btn.setEnabled(False)
        else:
            btn.setText("Скачать")
            btn.setEnabled(True)

    def on_installed(self, btn, files, warns):
        if sip.isdeleted(btn):
            self.installed_count += len(files)
            return
        btn.setText("Установлено")
        btn.setObjectName("installed")
        btn.style().unpolish(btn)
        btn.style().polish(btn)
        self.installed_count += len(files)
        self.counter.setText(f"Установлено за сессию: {self.installed_count}")
        txt = f"Скачано: {files[0]}"
        if len(files) > 1:
            txt += f". Зависимостей: {len(files) - 1}"
        if warns:
            txt += ". Ошибки: " + "; ".join(warns)
        self.status.setText(txt)

    def on_install_failed(self, btn, message):
        if sip.isdeleted(btn):
            return
        self._restore_btn(btn)
        self.status.setText(f"Ошибка: {message}")

    def on_manual(self, btn, url, message):
        if not sip.isdeleted(btn):
            self._restore_btn(btn)
            self.status.setText(f"{message} Открыл страницу в браузере.")
        QDesktopServices.openUrl(QUrl(url))

    def closeEvent(self, event):
        self._cancel_icons()
        super().closeEvent(event)


# ===========================================================================
# ToolsDialog tabs
# ===========================================================================
class LogsTab(QWidget):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.autoscroll = True
        self.cb_auto = QPushButton("Автопрокрутка")
        self.cb_auto.setObjectName("toolsbtn")
        self.cb_auto.setCheckable(True)
        self.cb_auto.setChecked(True)
        self.cb_auto.clicked.connect(lambda: setattr(self, "autoscroll", self.cb_auto.isChecked()))

        copy_btn = QPushButton("Копировать")
        copy_btn.setObjectName("toolsbtn")
        copy_btn.clicked.connect(self.copy_all)
        save_btn = QPushButton("Сохранить")
        save_btn.setObjectName("toolsbtn")
        save_btn.clicked.connect(self.save_to_file)
        clear_btn = QPushButton("Очистить")
        clear_btn.setObjectName("toolsbtn")
        clear_btn.clicked.connect(lambda: self.console.clear())

        top = QHBoxLayout()
        top.setSpacing(6)
        top.addWidget(self.cb_auto)
        top.addStretch(1)
        top.addWidget(copy_btn)
        top.addWidget(save_btn)
        top.addWidget(clear_btn)

        self.console = QPlainTextEdit()
        self.console.setReadOnly(True)
        self.console.setMaximumBlockCount(20000)
        self.console.setPlaceholderText("Логи появятся здесь")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(8)
        layout.addLayout(top)
        layout.addWidget(self.console, 1)

    def append(self, text: str):
        cursor = self.console.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        fmt = QTextCharFormat()
        low = text.lower()
        if "error" in low or "exception" in low or "fatal" in low:
            fmt.setForeground(QColor(THEMES["dark"]["err"]))
        elif "warn" in low:
            fmt.setForeground(QColor(THEMES["dark"]["warn"]))
        elif "готов" in low or "запускаю" in low:
            fmt.setForeground(QColor(THEMES["dark"]["ok"]))
        cursor.insertText(text + "\n", fmt)
        self.console.setTextCursor(cursor)
        if self.autoscroll:
            sb = self.console.verticalScrollBar()
            sb.setValue(sb.maximum())

    def copy_all(self):
        QApplication.clipboard().setText(self.console.toPlainText())

    def save_to_file(self):
        path_str, _ = QFileDialog.getSaveFileName(self, "Сохранить логи", "frimer-logs.txt", "Text (*.txt)")
        if not path_str:
            return
        Path(path_str).write_text(self.console.toPlainText(), encoding="utf-8")
        QMessageBox.information(self, "Готово", f"Логи сохранены:\n{path_str}")


def _mr_loaders(loader: str) -> list:
    low = (loader or "").lower()
    return ["quilt", "fabric"] if low == "quilt" else [low]


def move_install_meta(folder: Path, old_name: str, new_name: str):
    try:
        for old, new in zip(meta_paths(folder, old_name), meta_paths(folder, new_name)):
            if old.exists():
                new.parent.mkdir(parents=True, exist_ok=True)
                old.replace(new)
    except OSError:
        pass


MR_API = "https://api.modrinth.com/v2"
_VERSION_RANK = {"release": 2, "beta": 1, "alpha": 0}


def check_mod_updates(mods_dir: Path, mc: str, loader: str, status=None):
    jars = [f for f in sorted(mods_dir.glob("*.jar")) if f.is_file()]
    if not jars:
        return []
    by_hash: dict = {}
    for f in jars:
        by_hash.setdefault(sha1_file(f), []).append(f)

    if status:
        status("Определяю моды на Modrinth...")
    try:
        r = requests.post(f"{MR_API}/version_files",
                          json={"hashes": list(by_hash), "algorithm": "sha1"},
                          headers=UA, timeout=30)
    except requests.RequestException as e:
        raise RuntimeError(f"Не могу подключиться к Modrinth: {e}") from e
    if r.status_code != 200:
        raise RuntimeError(f"Modrinth: ошибка {r.status_code}")
    current = r.json()

    projects: dict = {}
    for h, v in current.items():
        projects.setdefault(v["project_id"], []).append(h)
    if not projects:
        return []

    params = {"game_versions": json.dumps([mc]), "loaders": json.dumps(_mr_loaders(loader))}

    def versions_of(pid):
        rr = http_get(f"{MR_API}/project/{pid}/version", params=params)
        if rr.status_code != 200:
            raise RuntimeError(f"ошибка {rr.status_code}")
        return rr.json()

    if status:
        status(f"Проверяю обновления ({len(projects)} модов)...")
    latest: dict = {}
    failed = 0
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = {pool.submit(versions_of, pid): pid for pid in projects}
        for fut in as_completed(futures):
            try:
                latest[futures[fut]] = fut.result()
            except Exception:
                failed += 1
    if failed == len(projects):
        raise RuntimeError("Не удалось проверить моды: проблема с сетью.")

    updates = []
    for pid, hashes in projects.items():
        versions = latest.get(pid) or []
        for h in hashes:
            cur = current[h]
            cur_rank = _VERSION_RANK.get(cur.get("version_type"), 2)
            found = None
            for v in versions:
                if v.get("id") == cur.get("id"):
                    break
                if v.get("date_published", "") <= cur.get("date_published", ""):
                    break
                if _VERSION_RANK.get(v.get("version_type"), 0) < cur_rank:
                    continue
                found = v
                break
            if not found:
                continue
            vfiles = found.get("files") or []
            if not vfiles:
                continue
            f = next((x for x in vfiles if x.get("primary")), vfiles[0])
            fh = f.get("hashes") or {}
            if (fh.get("sha1") or "").lower() == h.lower():
                continue
            for path in by_hash[h]:
                title, icon = load_install_meta(mods_dir, path.name)
                if not title or not icon:
                    ft, fi = read_file_meta(path)
                    title, icon = title or ft, icon or fi
                updates.append({
                    "old_path": str(path), "title": title or path.stem, "icon": icon,
                    "current": cur.get("version_number", "?"), "new": found.get("version_number", "?"),
                    "filename": f["filename"], "url": f["url"],
                    "sha1": fh.get("sha1"), "sha512": fh.get("sha512"),
                })
    updates.sort(key=lambda u: u["title"].lower())
    return updates


def apply_mod_updates(updates: list, mods_dir: Path, progress):
    ok, errors = 0, []
    for i, u in enumerate(updates, 1):
        try:
            out = safe_join(mods_dir, u["filename"])
            download_file(u["url"], out, sha1=u.get("sha1"), sha512=u.get("sha512"),
                          log=lambda _: None)
            old = Path(u["old_path"])
            if old.resolve() != out.resolve():
                try:
                    old.unlink(missing_ok=True)
                except OSError as e:
                    out.unlink(missing_ok=True)
                    raise RuntimeError(f"старый файл занят ({e})")
                move_install_meta(mods_dir, old.name, out.name)
            ok += 1
        except Exception as e:
            errors.append(f"{u['title']}: {e}")
        progress(i, len(updates))
    return ok, errors


class UpdateCheckWorker(QThread):
    status = pyqtSignal(str)
    done = pyqtSignal(list)
    failed = pyqtSignal(str)

    def __init__(self, mods_dir, mc, loader):
        super().__init__()
        self.mods_dir, self.mc, self.loader = mods_dir, mc, loader

    def run(self):
        try:
            self.done.emit(check_mod_updates(self.mods_dir, self.mc, self.loader, self.status.emit))
        except Exception as e:
            self.failed.emit(str(e))


class UpdateApplyWorker(QThread):
    progress = pyqtSignal(int, int)
    done = pyqtSignal(int, list)

    def __init__(self, updates, mods_dir):
        super().__init__()
        self.updates, self.mods_dir = updates, mods_dir

    def run(self):
        ok, errors = apply_mod_updates(self.updates, self.mods_dir,
                                       lambda v, m: self.progress.emit(v, m))
        self.done.emit(ok, errors)


class UpdatesDialog(QDialog):
    def __init__(self, parent, inst, mods_dir: Path, theme: str):
        super().__init__(parent)
        self.mods_dir = mods_dir
        self.setWindowTitle("Обновление модов")
        self.resize(640, 520)
        self.setStyleSheet(build_style(theme))

        head = QLabel(f"Обновления модов - {inst.loader} {inst.mc_version}")
        head.setObjectName("status")
        self.info = QLabel("Проверяю...")
        self.info.setObjectName("subtitle")
        self.info.setWordWrap(True)

        self.list = QListWidget()
        self.list.setObjectName("filelist")
        self.list.setIconSize(QSize(36, 36))

        self.bar = QProgressBar()
        self.bar.setTextVisible(False)
        self.bar.setMaximum(0)

        self.all_btn = QPushButton("Выбрать все / снять")
        self.all_btn.setObjectName("toolsbtn")
        self.all_btn.clicked.connect(self._toggle_all)
        self.apply_btn = QPushButton("Обновить выбранные")
        self.apply_btn.setObjectName("toolsbtn")
        self.apply_btn.clicked.connect(self._apply)
        close_btn = QPushButton("Закрыть")
        close_btn.setObjectName("toolsbtn")
        close_btn.clicked.connect(self.accept)
        self.all_btn.setEnabled(False)
        self.apply_btn.setEnabled(False)

        row = QHBoxLayout()
        row.addWidget(self.all_btn)
        row.addStretch(1)
        row.addWidget(self.apply_btn)
        row.addWidget(close_btn)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 14)
        layout.setSpacing(10)
        layout.addWidget(head)
        layout.addWidget(self.info)
        layout.addWidget(self.bar)
        layout.addWidget(self.list, 1)
        layout.addLayout(row)

        self._busy = True
        self._checker = UpdateCheckWorker(mods_dir, inst.mc_version, inst.loader)
        self._checker.status.connect(self.info.setText)
        self._checker.done.connect(self._on_checked)
        self._checker.failed.connect(self._on_failed)
        self._checker.start()

    def _on_failed(self, msg):
        self._busy = False
        self.bar.setMaximum(1)
        self.bar.setValue(0)
        self.info.setText(msg)

    def _on_checked(self, updates):
        self._busy = False
        self.bar.setMaximum(1)
        self.bar.setValue(0)
        self.list.clear()
        if not updates:
            self.info.setText("Всё свежее: обновлений нет.")
            return
        for u in updates:
            item = QListWidgetItem(f"{u['title']}\n{u['current']}  ->  {u['new']}")
            pix = pixmap_from_bytes(u.get("icon"), 36) or letter_icon(u["title"], 36)
            item.setIcon(QIcon(pix))
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked)
            item.setData(Qt.ItemDataRole.UserRole, u)
            self.list.addItem(item)
        self.info.setText(f"Доступно обновлений: {len(updates)}.")
        self.all_btn.setEnabled(True)
        self.apply_btn.setEnabled(True)

    def _toggle_all(self):
        items = [self.list.item(i) for i in range(self.list.count())]
        new_state = (Qt.CheckState.Unchecked
                     if all(it.checkState() == Qt.CheckState.Checked for it in items)
                     else Qt.CheckState.Checked)
        for it in items:
            it.setCheckState(new_state)

    def _apply(self):
        chosen = [self.list.item(i).data(Qt.ItemDataRole.UserRole)
                  for i in range(self.list.count())
                  if self.list.item(i).checkState() == Qt.CheckState.Checked]
        if not chosen:
            return
        self._busy = True
        self.apply_btn.setEnabled(False)
        self.all_btn.setEnabled(False)
        self.bar.setMaximum(len(chosen))
        self.bar.setValue(0)
        self.info.setText("Обновляю...")
        self._applier = UpdateApplyWorker(chosen, self.mods_dir)
        self._applier.progress.connect(lambda v, m: (self.bar.setMaximum(m or 1), self.bar.setValue(v)))
        self._applier.done.connect(self._on_applied)
        self._applier.start()

    def _on_applied(self, ok, errors):
        self._busy = False
        self.list.clear()
        self.bar.setValue(self.bar.maximum())
        text = f"Обновлено модов: {ok}."
        if errors:
            text += "\nНе удалось:\n" + "\n".join(errors[:6])
        self.info.setText(text)

    def reject(self):
        if not self._busy:
            super().reject()

    def accept(self):
        if not self._busy:
            super().accept()


class FileListTab(QWidget):
    def __init__(self, inst, launcher, folder: Path, title: str, kind: str,
                 mc_version: str = None, loader_name: str = None, parent=None,
                 server: bool = False):
        super().__init__(parent)
        self.inst = inst
        self.server = server
        self.launcher = launcher
        self.folder = folder
        self.kind = kind
        self.mc_version = mc_version if mc_version is not None else (inst.mc_version if inst else None)
        self.loader_name = loader_name if loader_name is not None else (inst.loader if inst else None)

        head = QLabel(title)
        head.setObjectName("status")
        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("Поиск среди установленного...")
        self.search_edit.textChanged.connect(self.filter_list)
        self.list = QListWidget()
        self.list.setObjectName("filelist")
        self.list.setIconSize(QSize(40, 40))
        self.list.itemChanged.connect(self.on_item_checked)

        refresh = QPushButton("Обновить")
        refresh.setObjectName("toolsbtn")
        refresh.clicked.connect(self.refresh)
        dl = QPushButton("Скачать...")
        dl.setObjectName("toolsbtn")
        dl.clicked.connect(self.download_clicked)
        toggle = QPushButton("Вкл / Выкл")
        toggle.setObjectName("toolsbtn")
        toggle.clicked.connect(self.toggle_selected)
        openf = QPushButton("Открыть папку")
        openf.setObjectName("toolsbtn")
        openf.clicked.connect(self.open_folder)
        remove = QPushButton("Удалить")
        remove.setObjectName("toolsbtn")
        remove.clicked.connect(self.delete_selected)

        row = QHBoxLayout()
        row.setSpacing(6)
        row.addWidget(refresh)
        row.addWidget(dl)
        if kind == "mods":
            upd = QPushButton("Обновления")
            upd.setObjectName("toolsbtn")
            upd.clicked.connect(self.check_updates)
            row.addWidget(upd)
        row.addWidget(toggle)
        row.addWidget(openf)
        row.addStretch(1)
        row.addWidget(remove)

        self.info = QLabel("")
        self.info.setObjectName("subtitle")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(8)
        layout.addWidget(head)
        layout.addWidget(self.search_edit)
        layout.addWidget(self.list, 1)
        layout.addLayout(row)
        layout.addWidget(self.info)

        self.refresh()

    def refresh(self):
        self.list.blockSignals(True)
        try:
            self._refresh()
        finally:
            self.list.blockSignals(False)
        self.filter_list(self.search_edit.text())

    def filter_list(self, text: str):
        needle = text.strip().lower()
        for i in range(self.list.count()):
            item = self.list.item(i)
            item.setHidden(bool(needle) and needle not in item.text().lower())

    def _refresh(self):
        self.list.clear()
        if not self.folder.exists():
            self.info.setText(f"Папка не найдена: {self.folder}")
            return
        cnt = 0
        off = 0
        for f in sorted(self.folder.iterdir()):
            if not f.is_file():
                continue
            disabled = f.name.endswith(".disabled")
            base = f.name[:-9] if disabled else f.name
            if Path(base).suffix.lower() not in (".jar", ".zip"):
                continue
            title, icon_data = load_install_meta(self.folder, base)
            if not title or not icon_data:
                file_title, file_icon = read_file_meta(f)
                title = title or file_title
                icon_data = icon_data or file_icon
            shown = title or Path(base).stem
            text = f"{shown}\n{base}" if title else base
            if disabled:
                text = "[выкл] " + text
            item = QListWidgetItem(text)
            pix = pixmap_from_bytes(icon_data, 40) or letter_icon(shown, 40)
            item.setIcon(QIcon(pix))
            item.setData(Qt.ItemDataRole.UserRole, str(f))
            item.setData(Qt.ItemDataRole.UserRole + 1, shown if title else "")
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Unchecked if disabled else Qt.CheckState.Checked)
            if disabled:
                item.setForeground(QBrush(QColor(MUTED_TEXT)))
                off += 1
            self.list.addItem(item)
            cnt += 1
        self.info.setText(f"Файлов: {cnt}" + (f" (выключено: {off})" if off else ""))

    def on_item_checked(self, item):
        p = Path(item.data(Qt.ItemDataRole.UserRole))
        want_on = item.checkState() == Qt.CheckState.Checked
        is_off = p.name.endswith(".disabled")
        if want_on != is_off:
            return
        new = p.with_name(p.name[:-9]) if is_off else p.with_name(p.name + ".disabled")
        self.list.blockSignals(True)
        try:
            if new.exists():
                raise OSError(f"файл {new.name} уже существует")
            p.rename(new)
            base = new.name[:-9] if new.name.endswith(".disabled") else new.name
            shown = item.data(Qt.ItemDataRole.UserRole + 1)
            text = f"{shown}\n{base}" if shown else base
            if not want_on:
                text = "[выкл] " + text
            item.setText(text)
            item.setData(Qt.ItemDataRole.UserRole, str(new))
            item.setForeground(QBrush(QColor(MUTED_TEXT)) if not want_on else QBrush())
        except OSError as e:
            item.setCheckState(Qt.CheckState.Unchecked if want_on else Qt.CheckState.Checked)
            QMessageBox.warning(self, "Ошибка",
                                f"Не удалось {'включить' if want_on else 'выключить'} файл "
                                f"(возможно, он занят игрой или сервером):\n{e}")
        finally:
            self.list.blockSignals(False)
        total = self.list.count()
        off = sum(1 for i in range(total) if self.list.item(i).checkState() == Qt.CheckState.Unchecked)
        self.info.setText(f"Файлов: {total}" + (f" (выключено: {off})" if off else ""))

    def _selected(self):
        item = self.list.currentItem()
        if not item:
            return None
        return Path(item.data(Qt.ItemDataRole.UserRole))

    def download_clicked(self):
        if self.kind == "mods":
            if self.loader_name == "Vanilla":
                QMessageBox.information(self, "Нужен загрузчик",
                                        "Моды работают только с Fabric, Quilt, Forge или NeoForge.")
                return
            menu = QMenu(self)
            mr = menu.addAction("Modrinth")
            cf = menu.addAction("CurseForge")
            act = menu.exec(self.cursor().pos())
            if act == mr:
                provider = ModrinthServerModProvider() if self.server else ModrinthProvider()
            elif act == cf:
                provider = self.launcher._make_cf()
                if not provider:
                    return
            else:
                return
        elif self.kind == "resourcepacks":
            provider = ModrinthResourcepackProvider()
        elif self.kind == "shaders":
            provider = ModrinthShaderProvider()
        elif self.kind == "plugins":
            provider = ModrinthPluginProvider()
        else:
            return
        self.folder.mkdir(parents=True, exist_ok=True)
        dlg = ModBrowser(self, provider, self.loader_name, self.mc_version,
                         self.folder, self.launcher.settings)
        dlg.exec()
        self.refresh()

    def check_updates(self):
        if self.loader_name == "Vanilla":
            QMessageBox.information(self, "Нужен загрузчик",
                                    "Моды работают только с Fabric, Quilt, Forge или NeoForge.")
            return
        inst = self.inst or SimpleNamespace(loader=self.loader_name, mc_version=self.mc_version)
        dlg = UpdatesDialog(self, inst, self.folder,
                            self.launcher.settings.value("theme", "dark"))
        dlg.exec()
        self.refresh()

    def toggle_selected(self):
        p = self._selected()
        if not p:
            return
        item = self.list.currentItem()
        item.setCheckState(Qt.CheckState.Unchecked if item.checkState() == Qt.CheckState.Checked
                           else Qt.CheckState.Checked)

    def delete_selected(self):
        p = self._selected()
        if not p:
            return
        if QMessageBox.question(self, "Удалить", f"Удалить файл?\n\n{p.name}") != QMessageBox.StandardButton.Yes:
            return
        p.unlink(missing_ok=True)
        delete_install_meta(self.folder, p.name[:-9] if p.name.endswith(".disabled") else p.name)
        self.refresh()

    def open_folder(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.folder)))


class ServerPluginsDialog(QDialog):
    def __init__(self, parent, srv, launcher):
        super().__init__(parent)
        self.setWindowTitle(f"Плагины — {srv.name}")
        self.resize(760, 640)
        self.setMinimumSize(600, 480)
        self.setStyleSheet(build_style(launcher.settings.value("theme", "dark")))

        self.tab = FileListTab(None, launcher, srv.folder / "plugins", "Плагины (Modrinth)",
                               "plugins", mc_version=srv.mc_version, loader_name="paper")
        root = QVBoxLayout(self)
        root.setContentsMargins(14, 14, 14, 14)
        root.addWidget(self.tab)


class ServerModsDialog(QDialog):
    def __init__(self, parent, srv, launcher):
        super().__init__(parent)
        self.setWindowTitle(f"Моды — {srv.name}")
        self.resize(760, 640)
        self.setMinimumSize(600, 480)
        self.setStyleSheet(build_style(launcher.settings.value("theme", "dark")))

        loader = CORE_LOADER.get(srv.core, srv.core.capitalize())
        self.tab = FileListTab(None, launcher, srv.folder / "mods",
                               f"Моды {loader} (Modrinth)", "mods",
                               mc_version=srv.mc_version, loader_name=loader, server=True)
        root = QVBoxLayout(self)
        root.setContentsMargins(14, 14, 14, 14)
        root.addWidget(self.tab)


class WorldsTab(QWidget):
    def __init__(self, folder: Path, launcher=None, mc_version: str = None, parent=None):
        super().__init__(parent)
        self.folder = folder
        self.launcher = launcher
        self.mc_version = mc_version
        head = QLabel("Миры")
        head.setObjectName("status")
        self.list = QListWidget()
        self.list.setObjectName("filelist")
        self.list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.list.customContextMenuRequested.connect(self._world_menu)

        refresh = QPushButton("Обновить")
        refresh.setObjectName("toolsbtn")
        refresh.clicked.connect(self.refresh)
        openf = QPushButton("Открыть папку")
        openf.setObjectName("toolsbtn")
        openf.clicked.connect(self.open_folder)
        datapack = QPushButton("Скачать датапак")
        datapack.setObjectName("toolsbtn")
        datapack.clicked.connect(self.download_datapack)
        delete = QPushButton("Удалить мир")
        delete.setObjectName("toolsbtn")
        delete.clicked.connect(self.delete_selected)

        row = QHBoxLayout()
        row.setSpacing(6)
        row.addWidget(refresh)
        row.addWidget(openf)
        row.addWidget(datapack)
        row.addStretch(1)
        row.addWidget(delete)

        self.info = QLabel("")
        self.info.setObjectName("subtitle")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(8)
        layout.addWidget(head)
        layout.addWidget(self.list, 1)
        layout.addLayout(row)
        layout.addWidget(self.info)
        self.refresh()

    def _world_menu(self, pos: QPoint):
        item = self.list.itemAt(pos)
        if not item:
            return
        self.list.setCurrentItem(item)
        menu = QMenu(self)
        menu.addAction("Скачать датапак...").triggered.connect(self.download_datapack)
        menu.addAction("Открыть папку мира").triggered.connect(self._open_selected_folder)
        menu.addSeparator()
        menu.addAction("Удалить мир").triggered.connect(self.delete_selected)
        menu.exec(self.list.mapToGlobal(pos))

    def _open_selected_folder(self):
        item = self.list.currentItem()
        if not item:
            return
        QDesktopServices.openUrl(QUrl.fromLocalFile(item.data(Qt.ItemDataRole.UserRole)))

    def download_datapack(self):
        item = self.list.currentItem()
        if not item:
            QMessageBox.information(self, "Нет мира", "Сначала выбери мир в списке.")
            return
        if not self.launcher:
            QMessageBox.information(self, "Недоступно", "Скачивание датапаков здесь недоступно.")
            return
        world = Path(item.data(Qt.ItemDataRole.UserRole))
        menu = QMenu(self)
        mr = menu.addAction("Modrinth")
        cf = menu.addAction("CurseForge")
        act = menu.exec(self.cursor().pos())
        if act == mr:
            provider = ModrinthDatapackProvider()
        elif act == cf:
            provider = self.launcher._make_cf(CurseForgeDatapackProvider)
            if not provider:
                return
        else:
            return
        dest = world / "datapacks"
        dest.mkdir(parents=True, exist_ok=True)
        dlg = ModBrowser(self, provider, "Datapack", self.mc_version, dest, self.launcher.settings)
        dlg.exec()
        self.refresh()

    def refresh(self):
        self.list.clear()
        if not self.folder.exists():
            self.info.setText(f"Папка не найдена: {self.folder}")
            return
        cnt = 0
        for d in sorted(self.folder.iterdir()):
            if d.is_dir():
                item = QListWidgetItem(f"{d.name}  [{fmt_size(dir_size(d))}]")
                item.setData(Qt.ItemDataRole.UserRole, str(d))
                self.list.addItem(item)
                cnt += 1
        self.info.setText(f"Миров: {cnt}")

    def open_folder(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.folder)))

    def delete_selected(self):
        item = self.list.currentItem()
        if not item:
            return
        p = Path(item.data(Qt.ItemDataRole.UserRole))
        if QMessageBox.question(self, "Удалить", f"Удалить мир \"{p.name}\"?") != QMessageBox.StandardButton.Yes:
            return
        shutil.rmtree(p, ignore_errors=True)
        self.refresh()


class ScreenshotsTab(QWidget):
    THUMB = QSize(220, 124)
    MAX_SHOWN = 300
    EXTS = (".png", ".jpg", ".jpeg", ".bmp", ".webp")

    def __init__(self, folder: Path, parent=None):
        super().__init__(parent)
        self.folder = folder
        head = QLabel("Скрины")
        head.setObjectName("status")
        self.list = QListWidget()
        self.list.setObjectName("filelist")
        self.list.setViewMode(QListWidget.ViewMode.IconMode)
        self.list.setIconSize(self.THUMB)
        self.list.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.list.setMovement(QListWidget.Movement.Static)
        self.list.setWordWrap(True)
        self.list.setSpacing(8)
        self.list.setUniformItemSizes(True)
        self.list.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.list.customContextMenuRequested.connect(self._menu)
        self.list.itemDoubleClicked.connect(lambda _i: self.open_selected())

        refresh = QPushButton("Обновить")
        refresh.setObjectName("toolsbtn")
        refresh.clicked.connect(self.refresh)
        openf = QPushButton("Открыть папку")
        openf.setObjectName("toolsbtn")
        openf.clicked.connect(self.open_folder)
        copy = QPushButton("Копировать")
        copy.setObjectName("toolsbtn")
        copy.clicked.connect(self.copy_selected)
        delete = QPushButton("Удалить")
        delete.setObjectName("toolsbtn")
        delete.clicked.connect(self.delete_selected)

        row = QHBoxLayout()
        row.setSpacing(6)
        row.addWidget(refresh)
        row.addWidget(openf)
        row.addWidget(copy)
        row.addStretch(1)
        row.addWidget(delete)

        self.info = QLabel("")
        self.info.setObjectName("subtitle")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(8)
        layout.addWidget(head)
        layout.addWidget(self.list, 1)
        layout.addLayout(row)
        layout.addWidget(self.info)
        self.refresh()

    def _thumb(self, path: Path) -> QIcon:
        reader = QImageReader(str(path))
        reader.setAutoTransform(True)
        size = reader.size()
        if size.isValid():
            size.scale(self.THUMB, Qt.AspectRatioMode.KeepAspectRatio)
            reader.setScaledSize(size)
        img = reader.read()
        return QIcon(QPixmap.fromImage(img)) if not img.isNull() else QIcon()

    def refresh(self):
        self.list.clear()
        if not self.folder.exists():
            self.info.setText("Скринов пока нет: сделай скриншот в игре (F2).")
            return
        files = [f for f in self.folder.iterdir()
                 if f.is_file() and f.suffix.lower() in self.EXTS]
        files.sort(key=lambda f: f.stat().st_mtime, reverse=True)
        for f in files[:self.MAX_SHOWN]:
            item = QListWidgetItem(self._thumb(f), f.stem)
            item.setData(Qt.ItemDataRole.UserRole, str(f))
            item.setSizeHint(QSize(self.THUMB.width() + 16, self.THUMB.height() + 44))
            self.list.addItem(item)
        total = len(files)
        if total == 0:
            self.info.setText("Скринов пока нет: сделай скриншот в игре (F2).")
        elif total > self.MAX_SHOWN:
            self.info.setText(f"Показаны {self.MAX_SHOWN} новейших из {total}")
        else:
            self.info.setText(f"Скринов: {total}")

    def _selected_paths(self) -> list:
        return [Path(i.data(Qt.ItemDataRole.UserRole)) for i in self.list.selectedItems()]

    def _menu(self, pos: QPoint):
        item = self.list.itemAt(pos)
        if not item:
            return
        if not item.isSelected():
            self.list.clearSelection()
            item.setSelected(True)
        menu = QMenu(self)
        menu.addAction("Открыть").triggered.connect(self.open_selected)
        menu.addAction("Копировать картинку").triggered.connect(self.copy_selected)
        menu.addAction("Показать в папке").triggered.connect(self.open_folder)
        menu.addSeparator()
        menu.addAction("Удалить").triggered.connect(self.delete_selected)
        menu.exec(self.list.mapToGlobal(pos))

    def open_selected(self):
        for p in self._selected_paths()[:1]:
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(p)))

    def copy_selected(self):
        paths = self._selected_paths()
        if not paths:
            return
        img = QImage(str(paths[0]))
        if not img.isNull():
            QApplication.clipboard().setImage(img)
            self.info.setText(f"Скопировано: {paths[0].name}")

    def open_folder(self):
        self.folder.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(self.folder)))

    def delete_selected(self):
        paths = self._selected_paths()
        if not paths:
            return
        what = f"«{paths[0].name}»" if len(paths) == 1 else f"скринов: {len(paths)}"
        if QMessageBox.question(self, "Удалить", f"Удалить {what}?") != QMessageBox.StandardButton.Yes:
            return
        for p in paths:
            try:
                p.unlink()
            except OSError:
                pass
        self.refresh()


class InfoTab(QWidget):
    def __init__(self, inst: Instance, launcher, parent=None):
        super().__init__(parent)
        head = QLabel(inst.name)
        head.setObjectName("status")
        sub = QLabel(f"{inst.loader} {inst.mc_version} - {inst.ram_mb / 1024:g} GB RAM")
        sub.setObjectName("subtitle")
        self.sub_label = sub
        self.inst = inst
        s = inst.get_stats()
        stats = QLabel(
            f"Всего наиграно: {fmt_duration(int(s.get('seconds', 0)))}\n"
            f"Сессий: {s.get('sessions', 0)}\n"
            f"Последний запуск: {s.get('last') or '-'}\n"
            f"Модов: {count_mods(inst.folder)}\n"
            f"Размер папки: {fmt_size(dir_size(inst.folder))}"
        )
        stats.setObjectName("subtitle")

        edit = QPushButton("Изменить")
        edit.setObjectName("toolsbtn")
        edit.clicked.connect(lambda: launcher.edit_instance(inst) or self._close())
        openf = QPushButton("Открыть папку")
        openf.setObjectName("toolsbtn")
        openf.clicked.connect(lambda: QDesktopServices.openUrl(QUrl.fromLocalFile(str(inst.folder))))
        delete = QPushButton("Удалить инстанс")
        delete.setObjectName("toolsbtn")
        delete.clicked.connect(lambda: launcher.delete_instance(inst) or self._close())

        row = QHBoxLayout()
        row.setSpacing(6)
        row.addWidget(edit)
        row.addWidget(openf)
        row.addStretch(1)
        row.addWidget(delete)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(10)
        layout.addWidget(head)
        layout.addWidget(sub)
        layout.addSpacing(6)
        layout.addWidget(stats)
        layout.addStretch(1)
        layout.addLayout(row)

    def _close(self):
        parent = self.parent()
        while parent and not isinstance(parent, ToolsDialog):
            parent = parent.parent()
        if parent:
            parent.close()


class LoaderTab(QWidget):
    AUTO = "Автоматически (последняя)"
    changed = pyqtSignal()

    def __init__(self, inst: Instance, launcher, log_slot=None, parent=None):
        super().__init__(parent)
        self.inst = inst
        self.launcher = launcher
        self._log_slot = log_slot
        self._versions_worker = None
        self._install_worker = None
        self._busy = False

        head = QLabel("Загрузчик")
        head.setObjectName("status")
        self.current = QLabel()
        self.current.setObjectName("subtitle")
        self.current.setWordWrap(True)
        self.installed = QLabel()
        self.installed.setObjectName("subtitle")
        self.installed.setWordWrap(True)

        self.loader_box = QComboBox()
        self.loader_box.addItems(LOADERS)
        self.loader_box.setCurrentText(inst.loader)
        self.version_box = QComboBox()
        self.version_box.setEnabled(False)

        form = QFormLayout()
        form.addRow("Загрузчик", self.loader_box)
        form.addRow("Версия", self.version_box)

        self.warn = QLabel("")
        self.warn.setObjectName("subtitle")
        self.warn.setWordWrap(True)

        self.apply_btn = QPushButton("Сохранить выбор")
        self.apply_btn.setObjectName("toolsbtn")
        self.apply_btn.clicked.connect(lambda: self._apply(install=False))
        self.install_btn = QPushButton("Установить сейчас")
        self.install_btn.setObjectName("toolsbtn")
        self.install_btn.clicked.connect(lambda: self._apply(install=True))
        row = QHBoxLayout()
        row.setSpacing(6)
        row.addWidget(self.apply_btn)
        row.addWidget(self.install_btn)
        row.addStretch(1)

        self.progress = QProgressBar()
        self.progress.setVisible(False)
        self.status = QLabel("")
        self.status.setObjectName("subtitle")
        self.status.setWordWrap(True)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(10)
        layout.addWidget(head)
        layout.addWidget(self.current)
        layout.addWidget(self.installed)
        layout.addSpacing(6)
        layout.addLayout(form)
        layout.addWidget(self.warn)
        layout.addLayout(row)
        layout.addWidget(self.progress)
        layout.addWidget(self.status)
        layout.addStretch(1)

        self.loader_box.currentTextChanged.connect(self._on_loader_changed)
        self._refresh_info()
        self._load_versions()

    def _refresh_info(self):
        inst = self.inst
        if inst.loader == "Vanilla":
            self.current.setText(f"Сейчас: Vanilla {inst.mc_version} — загрузчик не установлен")
            self.installed.setText("Чтобы ставить моды, выбери Fabric, Quilt, Forge или NeoForge ниже.")
            return
        ver = inst.loader_version or "авто (последняя при запуске)"
        self.current.setText(f"Сейчас: {inst.loader} {ver} для Minecraft {inst.mc_version}")
        have = installed_loader_versions(inst.loader, inst.mc_version)
        if have:
            self.installed.setText("Установлено на этом компьютере: " + ", ".join(have[:6])
                                   + (" …" if len(have) > 6 else ""))
        else:
            self.installed.setText("Ещё не установлен — поставится при первом запуске "
                                   "или кнопкой «Установить сейчас».")

    def _set_busy(self, busy: bool):
        self._busy = busy
        for w in (self.loader_box, self.apply_btn, self.install_btn):
            w.setEnabled(not busy)
        self.version_box.setEnabled(not busy and self.version_box.count() > 1)
        self.progress.setVisible(busy)
        if busy:
            self.progress.setRange(0, 0)

    def _selected_version(self):
        return self.version_box.currentData()

    def _on_loader_changed(self, _text=None):
        loader = self.loader_box.currentText()
        if loader != self.inst.loader:
            extra = ""
            n = count_mods(self.inst.folder)
            if n:
                extra = f" В папке mods сейчас {n} шт. — они могут не работать с новым загрузчиком."
            self.warn.setText(f"Смена загрузчика: {self.inst.loader} → {loader}.{extra}")
        else:
            self.warn.setText("")
        self._load_versions()

    def _load_versions(self):
        loader = self.loader_box.currentText()
        self.version_box.clear()
        if loader == "Vanilla":
            self.version_box.addItem("—", None)
            self.version_box.setEnabled(False)
            return
        self.version_box.addItem(self.AUTO, None)
        self.version_box.setEnabled(False)
        self.status.setText("Загружаю список версий...")
        w = LoaderVersionsWorker(loader, self.inst.mc_version)
        w.done.connect(self._on_versions)
        w.failed.connect(self._on_versions_failed)
        self._versions_worker = w
        spawn(w)

    def _on_versions_failed(self, msg):
        self.status.setText(msg)
        self.version_box.setEnabled(not self._busy and self.version_box.count() > 1)

    def _on_versions(self, loader, mc, vers):
        if loader != self.loader_box.currentText() or mc != self.inst.mc_version:
            return
        have = set(installed_loader_versions(loader, mc))
        shown = list(vers[:100])
        cur = self.inst.loader_version if loader == self.inst.loader else None
        if cur and cur not in shown:
            shown.insert(0, cur)
        self.version_box.clear()
        self.version_box.addItem(self.AUTO, None)
        for v in shown:
            self.version_box.addItem(v + ("   ✓ установлена" if v in have else ""), v)
        if cur:
            i = self.version_box.findData(cur)
            if i >= 0:
                self.version_box.setCurrentIndex(i)
        self.version_box.setEnabled(not self._busy)
        self.status.setText("" if shown else f"Для {loader} {mc} версий не найдено.")

    def _apply(self, install: bool):
        if self._busy:
            return
        loader = self.loader_box.currentText()
        ver = self._selected_version()
        if loader != self.inst.loader:
            n = count_mods(self.inst.folder)
            msg = f"Сменить загрузчик {self.inst.loader} → {loader}?"
            if n:
                msg += (f"\n\nВ папке mods {n} шт. Моды от другого загрузчика не заработают "
                        "— их придётся заменить.")
            if QMessageBox.question(self, "Смена загрузчика", msg) != QMessageBox.StandardButton.Yes:
                return
        self._save(loader, ver)
        if install and loader != "Vanilla":
            self._start_install(loader, ver)
        else:
            self.status.setText("Сохранено." if loader == "Vanilla" else
                                "Сохранено. Установка произойдёт при запуске игры.")

    def _save(self, loader: str, ver):
        self.inst.loader = loader
        self.inst.loader_version = ver if loader != "Vanilla" else None
        self.inst.save()
        self._refresh_info()
        self.warn.setText("")
        self.changed.emit()
        self.launcher.refresh_instances()

    def _start_install(self, loader: str, ver):
        self._set_busy(True)
        self.status.setText(f"Устанавливаю {loader}...")
        w = LoaderInstallWorker(loader, self.inst.mc_version, ver)
        w.status.connect(self.status.setText)
        w.progress.connect(self._on_progress)
        if self._log_slot:
            w.log.connect(self._log_slot)
        w.done.connect(self._on_install_done)
        w.failed.connect(self._on_install_failed)
        self._install_worker = w
        spawn(w)

    def _on_progress(self, value: int, maximum: int):
        if maximum > 0:
            self.progress.setRange(0, maximum)
            self.progress.setValue(min(value, maximum))
        else:
            self.progress.setRange(0, 0)

    def _on_install_done(self, loader: str, ver: str):
        self._set_busy(False)
        if ver:
            self.inst.loader_version = ver
            self.inst.save()
        self._refresh_info()
        self._load_versions()
        self.status.setText(f"Готово: {loader} {ver} установлен." if ver else f"Готово: {loader} установлен.")
        self.changed.emit()
        self.launcher.refresh_instances()

    def _on_install_failed(self, msg: str):
        self._set_busy(False)
        self.status.setText(f"Ошибка установки: {msg}")


PLATFORM_IDS = {"minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader",
                "forge", "neoforge", "fml", "javafml", "lowcodefml"}
_MOD_MANIFEST_CACHE: dict = {}


def _forge_version(z, names, version) -> str:
    if isinstance(version, str) and version and not version.startswith("${"):
        return version
    if "META-INF/MANIFEST.MF" in names:
        try:
            text = z.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
            for line in text.splitlines():
                if line.lower().startswith("implementation-version:"):
                    return line.split(":", 1)[1].strip()
        except Exception:
            pass
    return ""


def _parse_zip_manifest(z, depth: int = 0):
    names = set(z.namelist())

    def read_json(name):
        return json.loads(z.read(name).decode("utf-8-sig"), strict=False)

    def nested_ids(inner):
        try:
            with zipfile.ZipFile(io.BytesIO(z.read(inner))) as nz:
                sub = _parse_zip_manifest(nz, 1)
            return set(sub["provides"]) if sub else set()
        except Exception:
            return set()

    if "fabric.mod.json" in names:
        d = read_json("fabric.mod.json")
        mid = d.get("id")
        if not isinstance(mid, str) or not mid:
            return None
        deps: dict = {}
        for key, req in (("depends", True), ("recommends", False), ("suggests", False)):
            block = d.get(key)
            if isinstance(block, dict):
                for dep in block:
                    deps.setdefault(dep, req)
        provides = {mid} | {p for p in (d.get("provides") or []) if isinstance(p, str)}
        if depth == 0:
            for j in d.get("jars") or []:
                fn = j.get("file") if isinstance(j, dict) else None
                if isinstance(fn, str) and fn in names:
                    provides |= nested_ids(fn)
        name = d.get("name")
        return {"id": mid, "name": name if isinstance(name, str) and name else mid,
                "version": str(d.get("version", "")), "provides": provides, "depends": deps}

    if "quilt.mod.json" in names:
        ql = read_json("quilt.mod.json").get("quilt_loader") or {}
        mid = ql.get("id")
        if not isinstance(mid, str) or not mid:
            return None
        md = ql.get("metadata") or {}
        deps = {}
        for dep in ql.get("depends") or []:
            if isinstance(dep, str):
                deps.setdefault(dep, True)
            elif isinstance(dep, dict) and isinstance(dep.get("id"), str):
                deps.setdefault(dep["id"], not dep.get("optional", False))
        provides = {mid}
        for p in ql.get("provides") or []:
            if isinstance(p, str):
                provides.add(p)
            elif isinstance(p, dict) and isinstance(p.get("id"), str):
                provides.add(p["id"])
        if depth == 0:
            for j in ql.get("jars") or []:
                if isinstance(j, str) and j in names:
                    provides |= nested_ids(j)
        name = md.get("name")
        return {"id": mid, "name": name if isinstance(name, str) and name else mid,
                "version": str(ql.get("version", "")), "provides": provides, "depends": deps}

    for toml_name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        if toml_name not in names or not tomllib:
            continue
        d = tomllib.loads(z.read(toml_name).decode("utf-8", errors="replace"))
        mods = [m for m in (d.get("mods") or []) if isinstance(m, dict) and isinstance(m.get("modId"), str)]
        if not mods:
            return None
        ids = {m["modId"] for m in mods}
        deps = {}
        all_deps = d.get("dependencies") or {}
        if isinstance(all_deps, dict):
            for owner in ids:
                for dep in all_deps.get(owner) or []:
                    if not isinstance(dep, dict) or not isinstance(dep.get("modId"), str):
                        continue
                    did = dep["modId"]
                    if did in ids:
                        continue
                    kind = dep.get("type")
                    if isinstance(kind, str):
                        kind = kind.lower()
                        if kind not in ("required", "optional"):
                            continue
                        req = kind == "required"
                    else:
                        req = bool(dep.get("mandatory", True))
                    if req or did not in deps:
                        deps[did] = req
        provides = set(ids)
        if depth == 0:
            for n in names:
                if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                    provides |= nested_ids(n)
        first = mods[0]
        name = first.get("displayName")
        return {"id": first["modId"], "name": name if isinstance(name, str) and name else first["modId"],
                "version": _forge_version(z, names, first.get("version")),
                "provides": provides, "depends": deps}
    return None


def read_mod_manifest(path: Path):
    try:
        st = path.stat()
    except OSError:
        return None
    key = (str(path), st.st_mtime_ns, st.st_size)
    if key in _MOD_MANIFEST_CACHE:
        return _MOD_MANIFEST_CACHE[key]
    info = None
    try:
        with zipfile.ZipFile(path) as z:
            info = _parse_zip_manifest(z)
    except Exception:
        info = None
    _MOD_MANIFEST_CACHE[key] = info
    return info


def scan_mod_entries(mods_dir: Path) -> list:
    out = []
    try:
        files = sorted(p for p in mods_dir.iterdir() if p.is_file() and p.suffix.lower() == ".jar")
    except OSError:
        return out
    for f in files:
        info = read_mod_manifest(f)
        if info:
            out.append({**info, "file": f.name, "path": f, "parsed": True})
        else:
            out.append({"id": f.stem, "name": f.stem, "version": "", "provides": {f.stem},
                        "depends": {}, "file": f.name, "path": f, "parsed": False})
    return out


def _provider_map(entries: list) -> dict:
    prov: dict = {}
    for e in entries:
        for pid in e["provides"] | {e["id"]}:
            prov.setdefault(pid, e)
    return prov


def find_missing_required(entries: list) -> list:
    prov = _provider_map(entries)
    out = []
    for e in entries:
        for dep, req in e["depends"].items():
            if req and dep not in PLATFORM_IDS and dep not in prov:
                out.append((e, dep))
    return out


def analyze_removal(entries: list, selected: list) -> dict:
    by_file = {e["file"]: e for e in entries}
    gone = {n for n in selected if n in by_file}
    removed = [by_file[n] for n in selected if n in by_file]
    prov_before = _provider_map(entries)

    broken = []
    level = 0
    while True:
        level += 1
        active = [e for e in entries if e["file"] not in gone]
        prov_after = _provider_map(active)
        new = []
        for e in active:
            for dep, req in e["depends"].items():
                if req and dep not in PLATFORM_IDS and dep in prov_before and dep not in prov_after:
                    new.append((e, dep))
                    break
        if not new:
            break
        for e, dep in new:
            gone.add(e["file"])
            broken.append((e, dep, level))

    active = [e for e in entries if e["file"] not in gone]
    prov_after = _provider_map(active)
    degraded = []
    for e in active:
        for dep, req in e["depends"].items():
            if not req and dep in prov_before and dep not in prov_after:
                degraded.append((e, dep))

    still_needed = set()
    for e in active:
        still_needed.update(e["depends"].keys())
    orphans: list = []
    for gfile in gone:
        for dep, req in by_file[gfile]["depends"].items():
            if not req:
                continue
            p = prov_after.get(dep)
            if p is None or p in orphans:
                continue
            if not ((p["provides"] | {p["id"]}) & still_needed):
                orphans.append(p)
    return {"removed": removed, "broken": broken, "degraded": degraded, "orphans": orphans}


PERF_FILE = "perf.json"
PERF_KEEP = 200
PERF_MIN_SECONDS = 45
PERF_CHART_MAX = 40
_PERF_READY = re.compile(
    r"Sound engine started|OpenAL initialized|Created: \d+x\d+x\d+ minecraft:textures/atlas/blocks")
_PERF_MS = re.compile(r"Running (\d+)\s*ms")


def perf_grade(score: int) -> str:
    return "хорошо" if score >= 75 else "средне" if score >= 50 else "плохо"


def perf_penalties(s: dict) -> list:
    def clamp(x):
        return max(0.0, min(1.0, x))

    out = []
    st = s.get("startup_s")
    if st is not None:
        out.append(("Загрузка игры", round(25 * clamp((st - 30) / 90)), f"грузилась {st:.0f} с"))
    play_min = max((s.get("duration_s", 0) - (st or 0)) / 60.0, 1.0)
    ticks = int(s.get("lag_ticks", 0))
    out.append(("Лаги (Can't keep up)", round(35 * clamp(ticks / play_min / 150.0)),
                f"{s.get('lag_events', 0)} раз, пропущено ~{ticks} тиков" if ticks else "не замечено"))
    cpu = s.get("cpu_avg")
    if cpu is not None:
        out.append(("Нагрузка на процессор", round(20 * clamp((cpu - 35) / 50.0)),
                    f"в среднем {cpu:.0f}%"))
    peak, limit = s.get("ram_peak_mb"), s.get("ram_limit_mb")
    if peak and limit:
        out.append(("Память", round(10 * clamp((peak / limit - 1.0) / 0.3)),
                    f"пик {peak / 1024:.1f} GB при лимите {limit / 1024:.1f} GB"))
    err_pm = int(s.get("errors", 0)) / play_min
    out.append(("Ошибки в логе", round(10 * clamp(err_pm / 6.0)),
                f"{s.get('errors', 0)} шт." if s.get("errors") else "нет"))
    return out


def compute_perf_score(s: dict) -> int:
    score = 100 - sum(p for _, p, _ in perf_penalties(s))
    if s.get("crashed"):
        score = min(score, 20)
    return int(max(0, min(100, score)))


def snapshot_mods(mods_dir: Path) -> dict:
    snap = {}
    for e in scan_mod_entries(mods_dir):
        key = e["id"] if e["id"] not in snap else f"{e['id']}#{e['file']}"
        snap[key] = {"v": e["version"], "f": e["file"], "n": e["name"]}
    return snap


class PerfSampler(threading.Thread):
    def __init__(self, pid: int, interval: float = 2.0):
        super().__init__(daemon=True)
        self.pid = pid
        self.interval = interval
        self.samples: list = []
        self._stop_evt = threading.Event()

    def run(self):
        if psutil is None:
            return
        try:
            proc = psutil.Process(self.pid)
            ncpu = psutil.cpu_count() or 1
            proc.cpu_percent(None)
        except Exception:
            return
        t0 = time.time()
        while not self._stop_evt.wait(self.interval):
            try:
                rss = proc.memory_info().rss / 1048576.0
                cpu = min(proc.cpu_percent(None) / ncpu, 100.0)
            except Exception:
                break
            self.samples.append((time.time() - t0, rss, cpu))

    def stop(self):
        self._stop_evt.set()


class PerfRecorder:
    def __init__(self, ram_mb: int, mods: dict):
        self.ram_mb = int(ram_mb)
        self.mods = mods
        self.t0 = time.time()
        self.startup_s = None
        self.lag_events = 0
        self.lag_ticks = 0
        self.errors = 0

    def feed(self, line: str):
        if self.startup_s is None and _PERF_READY.search(line):
            self.startup_s = time.time() - self.t0
        if "Can't keep up" in line:
            self.lag_events += 1
            m = _PERF_MS.search(line)
            self.lag_ticks += int(m.group(1)) // 50 if m else 1
        elif "/ERROR]" in line:
            self.errors += 1

    def finish(self, exit_code: int, killed: bool, sampler=None):
        dur = time.time() - self.t0
        crashed = exit_code != 0 and not killed
        if dur < PERF_MIN_SECONDS and not crashed:
            return None
        samples = list(sampler.samples) if sampler is not None else []
        play = [x for x in samples if x[0] >= (self.startup_s if self.startup_s is not None else 60)] or samples
        s = {
            "ts": int(self.t0),
            "date": datetime.fromtimestamp(self.t0).strftime("%Y-%m-%d %H:%M"),
            "duration_s": int(dur),
            "startup_s": round(self.startup_s, 1) if self.startup_s is not None else None,
            "ram_limit_mb": self.ram_mb,
            "ram_peak_mb": int(max(x[1] for x in samples)) if samples else None,
            "cpu_avg": round(sum(x[2] for x in play) / len(play), 1) if play else None,
            "lag_events": self.lag_events,
            "lag_ticks": self.lag_ticks,
            "errors": self.errors,
            "exit_code": exit_code,
            "crashed": crashed,
            "killed": killed,
            "mods": self.mods,
        }
        s["score"] = compute_perf_score(s)
        return s


def load_perf_sessions(folder: Path) -> list:
    f = folder / PERF_FILE
    try:
        data = json.loads(f.read_text(encoding="utf-8"))
        sessions = data.get("sessions", []) if isinstance(data, dict) else []
        return [s for s in sessions if isinstance(s, dict) and "score" in s]
    except (OSError, json.JSONDecodeError):
        return []


def save_perf_session(folder: Path, session: dict):
    sessions = load_perf_sessions(folder)
    sessions.append(session)
    sessions = sessions[-PERF_KEEP:]
    tmp = folder / (PERF_FILE + ".tmp")
    try:
        tmp.write_text(json.dumps({"sessions": sessions}, ensure_ascii=False), encoding="utf-8")
        tmp.replace(folder / PERF_FILE)
    except OSError:
        pass


def diff_mods(prev: dict, cur: dict):
    added = [k for k in cur if k not in prev]
    removed = [k for k in prev if k not in cur]
    changed = [k for k in cur if k in prev and cur[k].get("v") != prev[k].get("v")]
    return added, removed, changed


def _baseline(sessions: list, i: int):
    prevs = [s["score"] for s in sessions[max(0, i - 2):i]]
    return sum(prevs) / len(prevs) if prevs else None


def perf_blame(sessions: list) -> list:
    res: dict = {}
    for i in range(1, len(sessions)):
        added, _removed, _changed = diff_mods(sessions[i - 1].get("mods", {}), sessions[i].get("mods", {}))
        base = _baseline(sessions, i)
        if not added or base is None:
            continue
        delta = sessions[i]["score"] - base
        for k in added:
            name = sessions[i]["mods"].get(k, {}).get("n") or k
            res.setdefault(k, {"name": name, "d": [], "group": 0})
            res[k]["d"].append(delta)
            res[k]["group"] = max(res[k]["group"], len(added))
    rows = [(k, v["name"], sum(v["d"]) / len(v["d"]), len(v["d"]), v["group"]) for k, v in res.items()]
    rows.sort(key=lambda r: r[2])
    return rows


def perf_verdict(sessions: list):
    if len(sessions) < 2:
        return 0.0, ""
    last = sessions[-1]
    base = _baseline(sessions, len(sessions) - 1)
    delta = last["score"] - base
    added, _removed, changed = diff_mods(sessions[-2].get("mods", {}), last.get("mods", {}))
    culprits = added + changed
    if delta <= -10 and culprits:
        names = ", ".join((last["mods"].get(k, {}).get("n") or k) for k in culprits[:5])
        more = f" и ещё {len(culprits) - 5}" if len(culprits) > 5 else ""
        verb = "после добавления/обновления" if changed else "после добавления"
        return delta, f"оценка упала с {base:.0f} до {last['score']} {verb}: {names}{more}"
    return delta, ""


def perf_mod_effect(sessions: list, key: str):
    with_ = [s["score"] for s in sessions if key in s.get("mods", {})]
    without = [s["score"] for s in sessions if key not in s.get("mods", {})]
    if not with_ or not without:
        return None
    return sum(with_) / len(with_), sum(without) / len(without), len(with_), len(without)


_ROLE_FILE = Qt.ItemDataRole.UserRole
_ROLE_PATH = Qt.ItemDataRole.UserRole + 1
_ROLE_LAZY = Qt.ItemDataRole.UserRole + 2


def _colored(text: str, color: str) -> str:
    return f'<span style="color:{color}">{text}</span>'


class ModsTreeTab(QWidget):
    def __init__(self, inst, launcher, mods_tab, parent=None):
        super().__init__(parent)
        self.inst = inst
        self.launcher = launcher
        self.mods_tab = mods_tab
        self.t = THEMES.get(launcher.settings.value("theme", "dark"), THEMES["dark"])
        self.entries: list = []
        self._by_file: dict = {}
        self._prov: dict = {}

        head = QLabel("Ветка модов")
        head.setObjectName("status")
        hint = QLabel("Ветка показывает, что нужно каждому моду для работы. Выбери мод "
                      "(Ctrl - несколько) и справа увидишь, что случится, если его убрать.")
        hint.setObjectName("subtitle")
        hint.setWordWrap(True)

        self.search = QLineEdit()
        self.search.setPlaceholderText("Поиск мода...")
        self.search.textChanged.connect(lambda _t: self.rebuild())
        self.flat_cb = QCheckBox("Плоским списком")
        self.flat_cb.toggled.connect(lambda _c: self.rebuild())
        srow = QHBoxLayout()
        srow.addWidget(self.search, 1)
        srow.addWidget(self.flat_cb)

        self.tree = QTreeWidget()
        self.tree.setObjectName("modtree")
        self.tree.setHeaderHidden(True)
        self.tree.setIconSize(QSize(24, 24))
        self.tree.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.tree.itemExpanded.connect(self._on_expand)
        self.tree.itemSelectionChanged.connect(self._on_selection)

        self.info = QLabel("")
        self.info.setObjectName("subtitle")
        self.info.setWordWrap(True)

        left = QWidget()
        lv = QVBoxLayout(left)
        lv.setContentsMargins(0, 0, 8, 0)
        lv.setSpacing(8)
        lv.addWidget(head)
        lv.addWidget(hint)
        lv.addLayout(srow)
        lv.addWidget(self.tree, 1)
        lv.addWidget(self.info)

        self.report = QTextBrowser()
        self.report.setObjectName("modreport")
        self.report.setOpenLinks(False)

        refresh = QPushButton("Обновить")
        refresh.setObjectName("toolsbtn")
        refresh.clicked.connect(lambda _=False: self.refresh())
        self.btn_off = QPushButton("Отключить выбранные")
        self.btn_off.setObjectName("toolsbtn")
        self.btn_off.clicked.connect(lambda _=False: self._disable(False))
        self.btn_off_all = QPushButton("Отключить вместе со сломавшимися")
        self.btn_off_all.setObjectName("toolsbtn")
        self.btn_off_all.clicked.connect(lambda _=False: self._disable(True))
        brow = QHBoxLayout()
        brow.setSpacing(6)
        brow.addWidget(refresh)
        brow.addStretch(1)
        brow.addWidget(self.btn_off)
        brow.addWidget(self.btn_off_all)

        right = QWidget()
        rv = QVBoxLayout(right)
        rv.setContentsMargins(8, 0, 0, 0)
        rv.setSpacing(8)
        rv.addWidget(self.report, 1)
        rv.addLayout(brow)

        split = QSplitter(Qt.Orientation.Horizontal)
        split.addWidget(left)
        split.addWidget(right)
        split.setStretchFactor(0, 4)
        split.setStretchFactor(1, 5)

        root = QVBoxLayout(self)
        root.setContentsMargins(14, 14, 14, 14)
        root.addWidget(split, 1)

        self.refresh()

    def refresh(self):
        self.entries = scan_mod_entries(self.inst.folder / "mods")
        self._by_file = {e["file"]: e for e in self.entries}
        self._prov = _provider_map(self.entries)
        self.rebuild()

    def _children_rows(self, e: dict, path: tuple) -> list:
        rows, seen = [], set()
        chain = set(path) | {e["file"]}
        for dep, req in sorted(e["depends"].items()):
            if dep in PLATFORM_IDS:
                continue
            p = self._prov.get(dep)
            if p is None:
                if req:
                    rows.append((None, dep, True))
                continue
            if p["file"] in chain or p["file"] in seen:
                continue
            seen.add(p["file"])
            rows.append((p, dep, req))
        rows.sort(key=lambda r: (r[0] is not None, (r[0]["name"].lower() if r[0] else r[1])))
        return rows

    def rebuild(self):
        needle = self.search.text().strip().lower()
        flat = self.flat_cb.isChecked() or bool(needle)
        self.tree.blockSignals(True)
        self.tree.clear()
        if flat:
            roots = [e for e in self.entries if not needle or needle in e["name"].lower()
                     or needle in e["id"].lower() or needle in e["file"].lower()]
        else:
            needed = set()
            for e in self.entries:
                for dep, req in e["depends"].items():
                    p = self._prov.get(dep)
                    if req and p is not None and p is not e:
                        needed.add(p["file"])
            roots = [e for e in self.entries if e["file"] not in needed] or list(self.entries)
        for e in sorted(roots, key=lambda x: x["name"].lower()):
            self._add_item(self.tree.invisibleRootItem(), e, ())
        self.tree.blockSignals(False)

        disabled = 0
        try:
            disabled = sum(1 for p in (self.inst.folder / "mods").iterdir() if p.name.endswith(".disabled"))
        except OSError:
            pass
        missing = find_missing_required(self.entries)
        unparsed = sum(1 for e in self.entries if not e["parsed"])
        parts = [f"Включено модов: {len(self.entries)}"]
        if disabled:
            parts.append(f"выключено: {disabled}")
        if missing:
            parts.append(f"не хватает зависимостей: {len(missing)}")
        if unparsed:
            parts.append(f"не удалось прочитать: {unparsed}")
        self.info.setText(" - ".join(parts))
        self._on_selection()

    def _add_item(self, parent, e: dict, path: tuple, optional: bool = False):
        item = QTreeWidgetItem(parent)
        label = f"{e['name']}  {e['version']}" if e["version"] else e["name"]
        if optional:
            label += "  (необязательно)"
        item.setText(0, label)
        _title, icon_data = read_file_meta(e["path"])
        item.setIcon(0, QIcon(pixmap_from_bytes(icon_data, 24) or letter_icon(e["name"], 24)))
        full = path + (e["file"],)
        item.setData(0, _ROLE_FILE, e["file"])
        item.setData(0, _ROLE_PATH, "\n".join(full))
        if optional:
            item.setForeground(0, QBrush(QColor(self.t["muted"])))
        if self._children_rows(e, path):
            item.setData(0, _ROLE_LAZY, "1")
            item.addChild(QTreeWidgetItem(["..."]))
        return item

    def _on_expand(self, item):
        if item.data(0, _ROLE_LAZY) != "1":
            return
        item.setData(0, _ROLE_LAZY, "0")
        item.takeChildren()
        e = self._by_file.get(item.data(0, _ROLE_FILE))
        if not e:
            return
        path = tuple(str(item.data(0, _ROLE_PATH) or "").split("\n"))
        for p, dep, req in self._children_rows(e, path):
            if p is None:
                bad = QTreeWidgetItem(item)
                bad.setText(0, f"⚠ нет мода: {dep}")
                bad.setForeground(0, QBrush(QColor(self.t["err"])))
                continue
            self._add_item(item, p, path, optional=not req)

    def _selected_files(self) -> list:
        out = []
        for it in self.tree.selectedItems():
            f = it.data(0, _ROLE_FILE)
            if isinstance(f, str) and f and f not in out:
                out.append(f)
        return out

    def _on_selection(self):
        files = self._selected_files()
        self.btn_off.setEnabled(bool(files))
        self.btn_off_all.setEnabled(bool(files))
        self.report.setHtml(self._report_html(files))

    @staticmethod
    def _nm(e: dict) -> str:
        return _esc(e["name"] + (f" {e['version']}" if e["version"] else ""))

    def _dep_nm(self, dep: str) -> str:
        p = self._prov.get(dep)
        return _esc(p["name"]) if p else f"<i>{_esc(dep)}</i>"

    def _report_html(self, files: list) -> str:
        t = self.t
        if not self.entries:
            return "<p>В папке mods нет включённых модов.</p>"
        if not files:
            h = "<h3>Выбери мод слева</h3><p>Покажу, что случится, если его убрать, и кто от него зависит.</p>"
            missing = find_missing_required(self.entries)
            if missing:
                h += _colored("<p><b>Уже сейчас не хватает зависимостей:</b></p>", t["err"]) + "<ul>"
                for e, dep in missing[:30]:
                    h += f"<li>{self._nm(e)} требует <i>{_esc(dep)}</i></li>"
                h += "</ul>"
            else:
                h += "<p>" + _colored("Все обязательные зависимости на месте.", t["ok"]) + "</p>"
            return h

        a = analyze_removal(self.entries, files)
        removed = a["removed"]
        names = ", ".join(_esc(e["name"]) for e in removed[:6])
        if len(removed) > 6:
            names += f" и ещё {len(removed) - 6}"
        h = f"<h3>Если убрать: {names}</h3>"

        if a["broken"]:
            direct = [x for x in a["broken"] if x[2] == 1]
            chain = [x for x in a["broken"] if x[2] > 1]
            h += "<p>" + _colored("<b>Игра не запустится</b>", t["err"]) + \
                 ", пока не отключить и эти моды - им нужен убираемый:</p><ul>"
            for e, dep, _lvl in direct:
                h += f"<li>{self._nm(e)} " + _colored(f"- нужен {self._dep_nm(dep)}", t["muted"]) + "</li>"
            h += "</ul>"
            if chain:
                h += "<p>И по цепочке (им нужны уже сломавшиеся):</p><ul>"
                for e, dep, _lvl in chain:
                    h += f"<li>{self._nm(e)} " + _colored(f"- нужен {self._dep_nm(dep)}", t["muted"]) + "</li>"
                h += "</ul>"
        else:
            h += "<p>" + _colored("Ничего не сломается: ни один установленный мод не требует это.", t["ok"]) + "</p>"

        if a["degraded"]:
            h += "<p>" + _colored("Запустятся, но потеряют необязательную связь:", t["warn"]) + "</p><ul>"
            for e, dep in a["degraded"][:20]:
                h += f"<li>{self._nm(e)} " + _colored(f"- без {self._dep_nm(dep)}", t["muted"]) + "</li>"
            h += "</ul>"
        if a["orphans"]:
            h += "<p>Возможно, станут не нужны (их требовали только убираемые моды):</p><ul>"
            for e in a["orphans"][:20]:
                h += f"<li>{self._nm(e)}</li>"
            h += "</ul>"

        if len(removed) == 1:
            e = removed[0]
            rows = self._children_rows(e, ())
            if rows:
                h += "<p><b>Самому моду нужны:</b></p><ul>"
                for p, dep, req in rows:
                    if p is None:
                        h += "<li>" + _colored(f"⚠ нет мода: {_esc(dep)}", t["err"]) + "</li>"
                    else:
                        h += f"<li>{self._nm(p)}" + ("" if req else _colored(" (необязательно)", t["muted"])) + "</li>"
                h += "</ul>"
            ids = e["provides"] | {e["id"]}
            users = [x for x in self.entries if x is not e and ids & set(x["depends"])]
            if users:
                h += "<p><b>Он нужен модам:</b> " + ", ".join(self._nm(x) for x in users[:15])
                h += (f" и ещё {len(users) - 15}" if len(users) > 15 else "") + "</p>"
            eff = perf_mod_effect(load_perf_sessions(self.inst.folder), e["id"])
            if eff:
                h += (f"<p><b>По твоим запускам:</b> с этим модом средняя оценка {eff[0]:.0f}/100 "
                      f"({eff[2]} зап.), без него - {eff[1]:.0f}/100 ({eff[3]} зап.). "
                      + _colored("Это среднее: на него влияют и другие изменения сборки.", t["muted"]) + "</p>")
        return h

    def _disable(self, with_broken: bool):
        files = self._selected_files()
        if not files:
            return
        a = analyze_removal(self.entries, files)
        targets = [e["file"] for e in a["removed"]]
        broken = [e["file"] for e, _d, _l in a["broken"]]
        if with_broken:
            targets += broken
            text = f"Отключить {len(targets)} мод(ов), включая сломавшиеся?"
        elif broken:
            lst = "\n".join("• " + e["name"] for e, _d, _l in a["broken"][:12])
            text = ("После этого игра не запустится: этим модам нужен отключаемый:\n\n"
                    f"{lst}\n\nВсё равно отключить только выбранные?")
        else:
            text = f"Отключить {len(targets)} мод(ов)?"
        text += "\n\nВключить обратно можно во вкладке «Моды»."
        if QMessageBox.question(self, "Отключить мод", text) != QMessageBox.StandardButton.Yes:
            return
        mods_dir = self.inst.folder / "mods"
        errors = []
        for fn in targets:
            p = mods_dir / fn
            new = p.with_name(p.name + ".disabled")
            try:
                if new.exists():
                    raise OSError(f"{new.name} уже существует")
                p.rename(new)
            except OSError as err:
                errors.append(f"{fn}: {err}")
        self.mods_tab.refresh()
        self.refresh()
        if errors:
            QMessageBox.warning(self, "Не всё получилось",
                                "Не удалось отключить (возможно, файл занят игрой):\n\n" + "\n".join(errors))


def _score_color(t: dict, score: int) -> str:
    return t["ok"] if score >= 75 else t["warn"] if score >= 50 else t["err"]


class PerfChart(QWidget):
    picked = pyqtSignal(int)

    def __init__(self, theme: str = "dark", parent=None):
        super().__init__(parent)
        self.t = THEMES.get(theme, THEMES["dark"])
        self.sessions: list = []
        self.offset = 0
        self.sel = -1
        self._pts: list = []
        self.setMinimumHeight(230)
        self.setMouseTracking(True)

    def set_sessions(self, sessions: list):
        self.sessions = sessions[-PERF_CHART_MAX:]
        self.offset = len(sessions) - len(self.sessions)
        self.sel = len(self.sessions) - 1
        self.update()

    def select(self, idx: int):
        self.sel = idx - self.offset
        self.update()

    def paintEvent(self, _event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        t = self.t
        w, h = self.width(), self.height()
        plot = QRectF(40, 14, max(w - 40 - 14, 10), max(h - 14 - 30, 10))

        def y_of(v):
            return plot.bottom() - plot.height() * v / 100.0

        for lo, hi, col in ((75, 100, t["ok"]), (50, 75, t["warn"]), (0, 50, t["err"])):
            c = QColor(col)
            c.setAlpha(26)
            p.fillRect(QRectF(plot.left(), y_of(hi), plot.width(), y_of(lo) - y_of(hi)), c)
        font = QFont(self.font())
        font.setPointSize(8)
        p.setFont(font)
        for v in (0, 25, 50, 75, 100):
            y = y_of(v)
            pen = QPen(QColor(t["border"]))
            pen.setStyle(Qt.PenStyle.DashLine)
            p.setPen(pen)
            p.drawLine(QPointF(plot.left(), y), QPointF(plot.right(), y))
            p.setPen(QColor(t["muted"]))
            p.drawText(QRectF(0, y - 8, 34, 16),
                       Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter, str(v))

        self._pts = []
        n = len(self.sessions)
        if n == 0:
            p.setPen(QColor(t["muted"]))
            p.drawText(plot, Qt.AlignmentFlag.AlignCenter, "Пока нет запусков - сыграй хотя бы минуту")
            p.end()
            return

        pad = 18.0
        xs = [plot.left() + pad + (plot.width() - 2 * pad) * (i / (n - 1) if n > 1 else 0.5) for i in range(n)]
        ys = [y_of(s["score"]) for s in self.sessions]

        for i in range(1, n):
            added, removed, changed = diff_mods(self.sessions[i - 1].get("mods", {}),
                                                self.sessions[i].get("mods", {}))
            if not (added or removed or changed):
                continue
            pen = QPen(QColor(t["accent"]))
            pen.setStyle(Qt.PenStyle.DotLine)
            p.setPen(pen)
            p.drawLine(QPointF(xs[i], plot.top()), QPointF(xs[i], plot.bottom()))
            label = " ".join(x for x in (f"+{len(added)}" if added else "", f"-{len(removed)}" if removed else "",
                                         f"~{len(changed)}" if changed else "") if x)
            p.setPen(QColor(t["accent"]))
            p.drawText(QRectF(xs[i] - 30, plot.top() - 2, 60, 14), Qt.AlignmentFlag.AlignCenter, label)

        if n > 1:
            pen = QPen(QColor(t["muted"]))
            pen.setWidthF(1.5)
            p.setPen(pen)
            for i in range(1, n):
                p.drawLine(QPointF(xs[i - 1], ys[i - 1]), QPointF(xs[i], ys[i]))

        for i, s in enumerate(self.sessions):
            col = QColor(_score_color(t, s["score"]))
            r = 6.5 if i == self.sel else 4.5
            if i == self.sel:
                ring = QPen(QColor(t["text"]))
                ring.setWidthF(2.0)
                p.setPen(ring)
            else:
                p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QBrush(col))
            p.drawEllipse(QPointF(xs[i], ys[i]), r, r)
            if s.get("crashed"):
                x = QPen(QColor(t["err"]))
                x.setWidthF(2.0)
                p.setPen(x)
                p.drawLine(QPointF(xs[i] - 5, ys[i] - 5), QPointF(xs[i] + 5, ys[i] + 5))
                p.drawLine(QPointF(xs[i] - 5, ys[i] + 5), QPointF(xs[i] + 5, ys[i] - 5))
            self._pts.append((QPointF(xs[i], ys[i]), self.offset + i))

        p.setPen(QColor(t["muted"]))
        stride = max(1, n // 8)
        for i, s in enumerate(self.sessions):
            if i % stride and i != n - 1:
                continue
            if i != n - 1 and (n - 1 - i) < stride:
                continue
            d = s.get("date", "")
            p.drawText(QRectF(xs[i] - 24, plot.bottom() + 6, 48, 16), Qt.AlignmentFlag.AlignCenter,
                       f"{d[8:10]}.{d[5:7]}" if len(d) >= 10 else "")
        p.end()

    def _hit(self, pos):
        best = None
        for pt, idx in self._pts:
            d = (pt.x() - pos.x()) ** 2 + (pt.y() - pos.y()) ** 2
            if d <= 14 * 14 and (best is None or d < best[0]):
                best = (d, idx)
        return None if best is None else best[1]

    def mouseMoveEvent(self, ev):
        idx = self._hit(ev.position())
        if idx is None:
            QToolTip.hideText()
            self.setCursor(Qt.CursorShape.ArrowCursor)
            return
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        i = idx - self.offset
        s = self.sessions[i]
        tip = f"{s.get('date', '')}\nОценка: {s['score']}/100 ({perf_grade(s['score'])})"
        if i > 0:
            added, removed, changed = diff_mods(self.sessions[i - 1].get("mods", {}), s.get("mods", {}))
            for sign, keys in (("+", added), ("-", removed), ("~", changed)):
                for k in keys[:4]:
                    src = s if sign != "-" else self.sessions[i - 1]
                    tip += f"\n{sign} {src.get('mods', {}).get(k, {}).get('n') or k}"
        QToolTip.showText(ev.globalPosition().toPoint(), tip, self)

    def mousePressEvent(self, ev):
        idx = self._hit(ev.position())
        if idx is not None:
            self.select(idx)
            self.picked.emit(idx)


class PerfTab(QWidget):
    def __init__(self, inst, launcher, parent=None):
        super().__init__(parent)
        self.inst = inst
        self.launcher = launcher
        theme = launcher.settings.value("theme", "dark")
        self.t = THEMES.get(theme, THEMES["dark"])
        self.sessions: list = []

        head = QLabel("Шкала игры")
        head.setObjectName("status")
        self.summary = QLabel("")
        self.summary.setWordWrap(True)
        self.summary.setTextFormat(Qt.TextFormat.RichText)

        self.chart = PerfChart(theme)
        self.chart.picked.connect(self._select)

        self.list = QListWidget()
        self.list.setObjectName("filelist")
        self.list.currentRowChanged.connect(self._on_row)

        self.details = QTextBrowser()
        self.details.setObjectName("modreport")
        self.details.setOpenLinks(False)

        split = QSplitter(Qt.Orientation.Horizontal)
        split.addWidget(self.list)
        split.addWidget(self.details)
        split.setStretchFactor(0, 4)
        split.setStretchFactor(1, 5)

        refresh = QPushButton("Обновить")
        refresh.setObjectName("toolsbtn")
        refresh.clicked.connect(lambda _=False: self.reload())
        clear = QPushButton("Очистить историю")
        clear.setObjectName("toolsbtn")
        clear.clicked.connect(lambda _=False: self._clear())
        row = QHBoxLayout()
        row.addWidget(refresh)
        row.addStretch(1)
        row.addWidget(clear)

        lay = QVBoxLayout(self)
        lay.setContentsMargins(14, 14, 14, 14)
        lay.setSpacing(8)
        lay.addWidget(head)
        lay.addWidget(self.summary)
        lay.addWidget(self.chart)
        lay.addWidget(split, 1)
        lay.addLayout(row)

        self.reload()

    def reload(self):
        t = self.t
        self.sessions = load_perf_sessions(self.inst.folder)
        self.chart.set_sessions(self.sessions)
        self.list.blockSignals(True)
        self.list.clear()
        for i in range(len(self.sessions) - 1, -1, -1):
            s = self.sessions[i]
            text = f"{s.get('date', '')}   {s['score']}/100"
            if s.get("crashed"):
                text += "   вылет"
            if i > 0:
                added, removed, changed = diff_mods(self.sessions[i - 1].get("mods", {}), s.get("mods", {}))
                bits = [f"+{len(added)}" if added else "", f"-{len(removed)}" if removed else "",
                        f"~{len(changed)}" if changed else ""]
                bits = [b for b in bits if b]
                if bits:
                    text += "   моды: " + " ".join(bits)
            item = QListWidgetItem(text)
            item.setForeground(QBrush(QColor(_score_color(t, s["score"]))))
            self.list.addItem(item)
        self.list.blockSignals(False)

        if not self.sessions:
            self.summary.setText(
                "Пока нет данных. Запусти игру и поиграй хотя бы минуту: после выхода здесь появится оценка. "
                "Дальше, когда будешь добавлять моды, будет видно, после какого игра стала работать хуже."
                + ("" if psutil else "<br>" + _colored(
                    "Не найден psutil: оценка не увидит загрузку процессора и памяти "
                    "(pip install psutil).", t["warn"])))
            self.details.setHtml("")
            return
        last = self.sessions[-1]
        delta, verdict = perf_verdict(self.sessions)
        text = (f"Последний запуск: <b>{_colored(str(last['score']) + '/100', _score_color(t, last['score']))}</b>"
                f" ({perf_grade(last['score'])})")
        if len(self.sessions) > 1:
            text += f", {delta:+.0f} к обычному"
        if verdict:
            text += "<br>" + _colored(_esc(verdict), t["err"])
        if not psutil:
            text += "<br>" + _colored("Без psutil не видна нагрузка на процессор и память (pip install psutil).",
                                      t["warn"])
        self.summary.setText(text)
        self._select(len(self.sessions) - 1)

    def _on_row(self, row: int):
        if row >= 0:
            self._select(len(self.sessions) - 1 - row, from_list=True)

    def _select(self, idx: int, from_list: bool = False):
        if not (0 <= idx < len(self.sessions)):
            return
        self.chart.select(idx)
        if not from_list:
            self.list.blockSignals(True)
            self.list.setCurrentRow(len(self.sessions) - 1 - idx)
            self.list.blockSignals(False)
        self.details.setHtml(self._session_html(idx) + self._suspects_html())

    def _session_html(self, i: int) -> str:
        t = self.t
        s = self.sessions[i]
        col = _score_color(t, s["score"])
        h = (f"<h3>{_esc(s.get('date', ''))} - "
             f"{_colored(str(s['score']) + '/100 (' + perf_grade(s['score']) + ')', col)}</h3>")
        h += f"<p>Играл: {fmt_duration(int(s.get('duration_s', 0)))}</p>"
        if s.get("crashed"):
            h += "<p>" + _colored(f"Игра завершилась с ошибкой (код {s.get('exit_code')}).", t["err"]) + "</p>"
        h += "<p><b>Из чего сложилась оценка:</b></p><ul>"
        for label, pts, detail in perf_penalties(s):
            mark = _colored(f"-{pts}", t["err"] if pts >= 8 else t["warn"]) if pts else _colored("0", t["ok"])
            h += f"<li>{_esc(label)}: {mark} " + _colored(f"- {_esc(detail)}", t["muted"]) + "</li>"
        h += "</ul>"
        if i > 0:
            cur, prev = s.get("mods", {}), self.sessions[i - 1].get("mods", {})
            added, removed, changed = diff_mods(prev, cur)
            if added or removed or changed:
                h += "<p><b>Что поменялось в сборке с прошлого запуска:</b></p><ul>"
                for k in added:
                    m = cur[k]
                    h += f"<li>{_colored('+', t['ok'])} {_esc(m.get('n') or k)} {_esc(m.get('v', ''))}</li>"
                for k in removed:
                    m = prev[k]
                    h += f"<li>{_colored('-', t['err'])} {_esc(m.get('n') or k)}</li>"
                for k in changed:
                    h += (f"<li>{_colored('~', t['warn'])} {_esc(cur[k].get('n') or k)}: "
                          f"{_esc(prev[k].get('v', ''))} → {_esc(cur[k].get('v', ''))}</li>")
                h += "</ul>"
        return h

    def _suspects_html(self) -> str:
        t = self.t
        rows = [r for r in perf_blame(self.sessions) if r[2] <= -5][:8]
        h = "<hr><p><b>Моды, после добавления которых оценка падала:</b></p>"
        if not rows:
            return h + "<p>" + _colored("Пока таких нет.", t["muted"]) + "</p>"
        h += "<ul>"
        for _k, name, d, n, group in rows:
            extra = f", добавлен вместе с ещё {group - 1}" if group > 1 else ""
            h += f"<li>{_esc(name)}: {_colored(f'{d:+.0f}', t['err'])} " + \
                 _colored(f"({n} раз{extra})", t["muted"]) + "</li>"
        h += "</ul><p>" + _colored(
            "Это косвенная оценка: если за раз добавлено несколько модов, вина делится между ними, "
            "а результат зависит и от того, что ты делал в игре.", t["muted"]) + "</p>"
        return h

    def _clear(self):
        if not self.sessions:
            return
        if QMessageBox.question(self, "Очистить историю",
                                "Удалить всю историю оценок этой сборки?") != QMessageBox.StandardButton.Yes:
            return
        try:
            (self.inst.folder / PERF_FILE).unlink(missing_ok=True)
        except OSError:
            pass
        self.reload()


class ToolsDialog(QDialog):
    def __init__(self, parent, inst: Instance, launcher, start_tab: str = "logs"):
        super().__init__(parent)
        self.inst = inst
        self.launcher = launcher
        self.setWindowTitle(f"Frimer — {inst.name}")
        self.resize(980, 640)
        self.setMinimumSize(820, 520)
        self.setStyleSheet(build_style(launcher.settings.value("theme", "dark")))

        self.logs_tab = LogsTab()
        self.mods_tab = FileListTab(inst, launcher, inst.folder / "mods", "Моды", "mods")
        self.rp_tab = FileListTab(inst, launcher, inst.folder / "resourcepacks", "Ресурспаки", "resourcepacks")
        self.sh_tab = FileListTab(inst, launcher, inst.folder / "shaderpacks", "Шейдеры", "shaders")
        self.w_tab = WorldsTab(inst.folder / "saves", launcher, inst.mc_version)
        self.shots_tab = ScreenshotsTab(inst.folder / "screenshots")
        self.loader_tab = LoaderTab(inst, launcher, log_slot=self.logs_tab.append)
        self.loader_tab.changed.connect(self._on_loader_changed)
        self.info_tab = InfoTab(inst, launcher)
        self.tree_tab = ModsTreeTab(inst, launcher, self.mods_tab)
        self.perf_tab = PerfTab(inst, launcher)

        self.stack = QStackedWidget()
        for w in (self.logs_tab, self.mods_tab, self.tree_tab, self.perf_tab, self.rp_tab, self.sh_tab,
                  self.w_tab, self.shots_tab, self.loader_tab, self.info_tab):
            self.stack.addWidget(w)

        self.nav = QListWidget()
        self.nav.setObjectName("toolsnav")
        for name in ("Логи", "Моды", "Ветка модов", "Шкала игры", "Ресурспаки", "Шейдеры", "Миры",
                     "Скрины", "Загрузчик", "Инфо"):
            self.nav.addItem(name)
        self.nav.setFixedWidth(130)
        self.nav.currentRowChanged.connect(self.stack.setCurrentIndex)
        self.nav.currentRowChanged.connect(
            lambda r: self.shots_tab.refresh() if self.stack.widget(r) is self.shots_tab else None)
        self.nav.currentRowChanged.connect(self._refresh_on_open)
        idx = {"logs": 0, "mods": 1, "modtree": 2, "perf": 3, "resourcepacks": 4, "shaders": 5,
               "worlds": 6, "screenshots": 7, "loader": 8, "info": 9}.get(start_tab, 0)
        self.nav.setCurrentRow(idx)

        root = QHBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        root.addWidget(self.nav)
        root.addWidget(self.stack, 1)

    def append_log(self, text: str):
        self.logs_tab.append(text)

    def _refresh_on_open(self, row: int):
        w = self.stack.widget(row)
        if w is self.tree_tab:
            self.tree_tab.refresh()
        elif w is self.perf_tab:
            self.perf_tab.reload()

    def _on_loader_changed(self):
        inst = self.inst
        for tab in (self.mods_tab, self.rp_tab, self.sh_tab):
            tab.loader_name = inst.loader
        self.info_tab.sub_label.setText(
            f"{inst.loader} {inst.mc_version} - {inst.ram_mb / 1024:g} GB RAM")


class ServerConsoleDialog(QDialog):
    def __init__(self, parent, srv: ServerInstance, runner: ServerRunner, theme: str):
        super().__init__(parent)
        self.srv = srv
        self.runner = runner
        self.setWindowTitle(f"Консоль сервера: {srv.name}")
        self.resize(900, 560)
        self.setStyleSheet(build_style(theme))
        self.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, False)

        head = QLabel(f"{srv.name} - {srv.core_label} {srv.mc_version} - порт {srv.port}")
        head.setObjectName("status")

        self.console = QPlainTextEdit()
        self.console.setReadOnly(True)
        self.console.setMaximumBlockCount(50000)
        self.console.setPlaceholderText("Логи сервера появятся здесь")

        self.input = QLineEdit()
        self.input.setPlaceholderText("Команда серверу, например: op Ник / list / whitelist add Ник")
        self.input.returnPressed.connect(self._send)

        send_btn = QPushButton("Отправить")
        send_btn.setObjectName("toolsbtn")
        send_btn.clicked.connect(self._send)
        copy_btn = QPushButton("Копировать всё")
        copy_btn.setObjectName("toolsbtn")
        copy_btn.clicked.connect(lambda: QApplication.clipboard().setText(self.console.toPlainText()))
        save_btn = QPushButton("Сохранить в файл")
        save_btn.setObjectName("toolsbtn")
        save_btn.clicked.connect(self._save)
        clear_btn = QPushButton("Очистить")
        clear_btn.setObjectName("toolsbtn")
        clear_btn.clicked.connect(lambda: self.console.clear())

        row_input = QHBoxLayout()
        row_input.addWidget(self.input, 1)
        row_input.addWidget(send_btn, 0)

        row_btns = QHBoxLayout()
        row_btns.addWidget(copy_btn)
        row_btns.addWidget(save_btn)
        row_btns.addStretch(1)
        row_btns.addWidget(clear_btn)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 16, 16, 16)
        layout.setSpacing(10)
        layout.addWidget(head)
        layout.addWidget(self.console, 1)
        layout.addLayout(row_input)
        layout.addLayout(row_btns)

        if runner:
            runner.log.connect(self.append)
            runner.status.connect(lambda s: self.append(f"[status] {s}"))

    def append(self, text: str):
        self.console.appendPlainText(text)
        sb = self.console.verticalScrollBar()
        sb.setValue(sb.maximum())

    def _send(self):
        cmd = self.input.text().strip()
        if not cmd:
            return
        self.input.clear()
        proc = self.runner._proc if self.runner else None
        if not proc or proc.poll() is not None:
            self.append("[ошибка] Сервер не запущен.")
            return
        try:
            proc.stdin.write(cmd + "\n")
            proc.stdin.flush()
            self.append(f"> {cmd}")
        except Exception as e:
            self.append(f"[ошибка отправки] {e}")

    def _save(self):
        path_str, _ = QFileDialog.getSaveFileName(
            self, "Сохранить лог сервера", f"server-{self.srv.name}.txt", "Text (*.txt)")
        if not path_str:
            return
        try:
            Path(path_str).write_text(self.console.toPlainText(), encoding="utf-8")
            QMessageBox.information(self, "Готово", f"Сохранено:\n{path_str}")
        except OSError as e:
            QMessageBox.warning(self, "Ошибка", str(e))

    def closeEvent(self, event):
        event.ignore()
        self.hide()


class NewServerDialog(QDialog):
    def __init__(self, parent):
        super().__init__(parent)
        self.setWindowTitle("Новый сервер")
        self.setMinimumWidth(460)

        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("Мой сервер")

        self.version_box = QComboBox()
        self.version_box.setEnabled(False)

        self.core_box = QComboBox()
        self.core_box.addItem("Paper (рекомендуется)", "paper")
        self.core_box.addItem("Vanilla (от Mojang)", "vanilla")
        self.core_box.addItem("Fabric (моды)", "fabric")
        self.core_box.addItem("Forge (моды)", "forge")
        self.core_box.addItem("NeoForge (моды)", "neoforge")

        self.core_hint = QLabel("")
        self.core_hint.setObjectName("subtitle")
        self.core_hint.setWordWrap(True)

        self._versions_cache: dict = {}
        self._wanted_version = ""

        self.ram = QSlider(Qt.Orientation.Horizontal)
        self.ram.setRange(2, 32)
        self.ram.setValue(4)
        self.ram_label = QLabel()
        self.ram_label.setObjectName("value")
        self.ram.valueChanged.connect(self._ram_update)
        self._ram_update()
        ram_row = QHBoxLayout()
        ram_row.addWidget(self.ram, 1)
        ram_row.addWidget(self.ram_label)

        self.port_edit = QLineEdit("25565")
        self.playit_edit = QLineEdit()
        self.playit_edit.setPlaceholderText("например expressing-doughty.tun.ply.gg")

        form = QFormLayout()
        form.addRow("Название", self.name_edit)
        form.addRow("Версия Minecraft", self.version_box)
        form.addRow("Ядро", self.core_box)
        form.addRow("RAM", ram_row)
        form.addRow("Порт", self.port_edit)
        form.addRow("Адрес playit", self.playit_edit)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Создать")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)

        self.status = QLabel("")
        self.status.setObjectName("subtitle")

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(self.core_hint)
        layout.addWidget(self.status)
        layout.addWidget(buttons)

        self.core_box.currentIndexChanged.connect(self._on_core_changed)
        self._on_core_changed()

    def _ram_update(self):
        self.ram_label.setText(f"{self.ram.value()} GB")

    def _on_core_changed(self, _index=None):
        core = self.core_box.currentData()
        self.core_hint.setText(CORE_HINTS.get(core, ""))
        self._wanted_version = self.version_box.currentText() or self._wanted_version
        self.version_box.clear()
        self.version_box.setEnabled(False)
        if core in self._versions_cache:
            self._apply_versions(self._versions_cache[core])
            return
        self.status.setText("Загружаю список версий...")
        w = ServerVersionsWorker(core)
        w.done.connect(self._on_versions)
        w.failed.connect(self._on_versions_failed)
        spawn(w)

    def _on_versions(self, core, versions):
        self._versions_cache[core] = versions
        if core == self.core_box.currentData():
            self._apply_versions(versions)

    def _on_versions_failed(self, core, msg):
        if core == self.core_box.currentData():
            self.status.setText(msg)

    def _apply_versions(self, versions):
        self.status.setText("")
        self.version_box.clear()
        self.version_box.addItems(versions)
        self.version_box.setEnabled(True)
        idx = self.version_box.findText(self._wanted_version) if self._wanted_version else -1
        self.version_box.setCurrentIndex(idx if idx >= 0 else 0)

    def values(self):
        try:
            port = int(self.port_edit.text().strip() or "25565")
        except ValueError:
            port = 25565
        return {
            "name": self.name_edit.text().strip() or "Новый сервер",
            "mc_version": self.version_box.currentText(),
            "core": self.core_box.currentData(),
            "ram_mb": self.ram.value() * 1024,
            "port": port,
            "playit_address": self.playit_edit.text().strip(),
        }


class ServerCard(QFrame):
    clicked = pyqtSignal(object)
    gear_clicked = pyqtSignal(object)

    def __init__(self, srv: ServerInstance, selected: bool):
        super().__init__()
        self.srv = srv
        self.setObjectName("instance_card")
        self.setProperty("selected", "true" if selected else "false")
        self.setFixedHeight(64)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        icon = QLabel()
        icon.setPixmap(loader_badge(CORE_NAMES.get(srv.core, "Vanilla"), 44))
        icon.setFixedSize(44, 44)

        name = QLabel(srv.name)
        name.setObjectName("inst_name")
        name.setMinimumWidth(1)
        sub = QLabel(f"● {srv.mc_version} — порт {srv.port}")
        sub.setObjectName("inst_sub")
        sub.setMinimumWidth(1)

        text = QVBoxLayout()
        text.setSpacing(0)
        text.addWidget(name)
        text.addWidget(sub)
        text.addStretch(1)

        gear = QPushButton("\u2699")
        gear.setObjectName("gear")
        gear.setFixedSize(28, 28)
        gear.clicked.connect(lambda: self.gear_clicked.emit(self.srv))

        h = QHBoxLayout(self)
        h.setContentsMargins(10, 8, 6, 8)
        h.setSpacing(10)
        h.addWidget(icon, 0, Qt.AlignmentFlag.AlignVCenter)
        h.addLayout(text, 1)
        h.addWidget(gear, 0, Qt.AlignmentFlag.AlignVCenter)

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit(self.srv)
        super().mousePressEvent(event)


# ===========================================================================
# Диалоги создания/редактирования/настроек/скинов
# ===========================================================================
class NewInstanceDialog(QDialog):
    def __init__(self, parent):
        super().__init__(parent)
        self.setWindowTitle("Новый инстанс")
        self.setMinimumWidth(420)
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("Мой пак")
        self.loader_box = QComboBox()
        self.loader_box.addItems(LOADERS)
        self.version_box = QComboBox()
        self.version_box.setEnabled(False)
        self.loader_version_box = QComboBox()
        self.loader_version_box.setEnabled(False)
        self.loader_version_box.addItem("Автоматически (последняя)")

        self.ram = QSlider(Qt.Orientation.Horizontal)
        self.ram.setRange(2, 32)
        self.ram.setValue(8)
        self.ram_label = QLabel()
        self.ram_label.setObjectName("value")
        self.ram.valueChanged.connect(self._ram_label)
        self._ram_label()
        ram_row = QHBoxLayout()
        ram_row.addWidget(self.ram, 1)
        ram_row.addWidget(self.ram_label)

        form = QFormLayout()
        form.addRow("Название", self.name_edit)
        form.addRow("Загрузчик", self.loader_box)
        form.addRow("Версия Minecraft", self.version_box)
        form.addRow("Версия загрузчика", self.loader_version_box)
        form.addRow("Память", ram_row)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Создать")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)

        self.status = QLabel("")
        self.status.setObjectName("subtitle")

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(self.status)
        root.addWidget(buttons)

        self.loader_box.currentTextChanged.connect(self._on_loader_changed)
        self.version_box.currentTextChanged.connect(self._on_mc_changed)

        self._load_mc_versions()

    def _ram_label(self):
        self.ram_label.setText(f"{self.ram.value() * 512 / 1024:g} GB")

    def _load_mc_versions(self):
        self.status.setText("Загружаю список версий Minecraft...")
        self._w = VersionsWorker("Vanilla")
        self._w.done.connect(self._on_versions)
        self._w.failed.connect(lambda m: self.status.setText(m))
        spawn(self._w)

    def _on_versions(self, loader, versions):
        self.version_box.clear()
        self.version_box.addItems(versions)
        self.version_box.setEnabled(True)
        self.status.setText("")
        self._on_mc_changed(self.version_box.currentText())

    def _on_loader_changed(self, loader):
        if loader == "Vanilla":
            self.loader_version_box.clear()
            self.loader_version_box.addItem("—")
            self.loader_version_box.setEnabled(False)
        else:
            self._on_mc_changed(self.version_box.currentText())

    def _on_mc_changed(self, mc):
        loader = self.loader_box.currentText()
        if loader == "Vanilla" or not mc:
            return
        self.loader_version_box.clear()
        self.loader_version_box.addItem("Автоматически (последняя)")
        self.loader_version_box.setEnabled(False)
        w = LoaderVersionsWorker(loader, mc)
        w.done.connect(self._on_loader_versions)
        w.failed.connect(lambda m: self.status.setText(m))
        spawn(w)

    def _on_loader_versions(self, loader, mc, vers):
        if loader != self.loader_box.currentText() or mc != self.version_box.currentText():
            return
        self.loader_version_box.clear()
        self.loader_version_box.addItem("Автоматически (последняя)")
        self.loader_version_box.addItems(vers[:50])
        self.loader_version_box.setEnabled(True)

    def values(self):
        lv = self.loader_version_box.currentText()
        if lv.startswith("Автоматически") or lv == "—":
            lv = None
        return {
            "name": self.name_edit.text().strip() or "Новый инстанс",
            "loader": self.loader_box.currentText(),
            "mc_version": self.version_box.currentText(),
            "loader_version": lv,
            "ram_mb": self.ram.value() * 512,
        }


class EditInstanceDialog(QDialog):
    def __init__(self, parent, inst: Instance):
        super().__init__(parent)
        self.inst = inst
        self.setWindowTitle(f"Настройки: {inst.name}")
        self.setMinimumWidth(420)
        self.name_edit = QLineEdit(inst.name)
        self.ram = QSlider(Qt.Orientation.Horizontal)
        self.ram.setRange(2, 32)
        self.ram.setValue(max(2, min(32, inst.ram_mb // 512)))
        self.ram_label = QLabel()
        self.ram_label.setObjectName("value")
        self.ram.valueChanged.connect(lambda: self.ram_label.setText(f"{self.ram.value() * 512 / 1024:g} GB"))
        self.ram_label.setText(f"{self.ram.value() * 512 / 1024:g} GB")
        ram_row = QHBoxLayout()
        ram_row.addWidget(self.ram, 1)
        ram_row.addWidget(self.ram_label)
        info = QLabel(f"{inst.loader} {inst.mc_version} — загрузчик и версия MC не меняются")
        info.setObjectName("subtitle")

        form = QFormLayout()
        form.addRow("Название", self.name_edit)
        form.addRow("Загрузчик", info)
        form.addRow("Память", ram_row)

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Сохранить")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)

        root = QVBoxLayout(self)
        root.addLayout(form)
        root.addWidget(buttons)

    def values(self):
        return {"name": self.name_edit.text().strip() or self.inst.name,
                "ram_mb": self.ram.value() * 512}


class SettingsDialog(QDialog):
    def __init__(self, parent, settings: QSettings):
        super().__init__(parent)
        self.settings = settings
        self.setWindowTitle("Настройки")
        self.setMinimumWidth(460)

        tabs = QTabWidget()

        general = QWidget()
        g = QFormLayout(general)
        self.nick_edit = QLineEdit(settings.value("nick", "Player"))
        self.nick_edit.setMaxLength(16)
        g.addRow("Никнейм", self.nick_edit)

        self.theme_box = QComboBox()
        self.theme_box.addItem("Тёмная", "dark")
        self.theme_box.addItem("Светлая", "light")
        idx = self.theme_box.findData(settings.value("theme", "dark"))
        if idx >= 0:
            self.theme_box.setCurrentIndex(idx)
        g.addRow("Тема", self.theme_box)

        self.bg_path = settings.value("bg_image", "", type=str)
        self.bg_box = QComboBox()
        self.bg_box.addItem("Фурри (лапки)", "furry")
        self.bg_box.addItem("Своя картинка", "image")
        self.bg_box.addItem("Обычный", "plain")
        bi = self.bg_box.findData(settings.value("bg_mode", "furry", type=str))
        if bi >= 0:
            self.bg_box.setCurrentIndex(bi)
        bg_pick = QPushButton("Выбрать картинку...")
        bg_pick.setObjectName("folder")
        bg_pick.clicked.connect(self._pick_bg)
        bg_row = QHBoxLayout()
        bg_row.addWidget(self.bg_box, 1)
        bg_row.addWidget(bg_pick, 0)
        g.addRow("Фон", bg_row)

        self.launch_anim = QCheckBox("Показывать анимацию при запуске игры")
        self.launch_anim.setChecked(settings.value("launch_anim", True, type=bool))
        g.addRow("Анимация", self.launch_anim)

        self.discord_idle = QCheckBox("Показывать статус, пока игра не запущена")
        self.discord_idle.setChecked(settings.value("discord_idle", True, type=bool))
        g.addRow("Discord", self.discord_idle)
        self.discord_id_edit = QLineEdit(settings.value("discord_client_id", "", type=str))
        self.discord_id_edit.setPlaceholderText("Application ID")
        g.addRow("Discord ID", self.discord_id_edit)

        folder_label = QLabel(str(GAME_DIR))
        folder_label.setObjectName("subtitle")
        openf = QPushButton("Открыть")
        openf.setObjectName("folder")
        openf.clicked.connect(lambda: QDesktopServices.openUrl(QUrl.fromLocalFile(str(GAME_DIR))))
        row = QHBoxLayout()
        row.addWidget(folder_label, 1)
        row.addWidget(openf, 0)
        g.addRow("Папка игры", row)
        tabs.addTab(general, "Основное")

        ai = QWidget()
        af = QFormLayout(ai)
        self.ai_key_edit = QLineEdit(settings.value("ai_api_key", "", type=str))
        self.ai_key_edit.setEchoMode(QLineEdit.EchoMode.Password)
        self.ai_key_edit.setPlaceholderText("sk-ant-...")
        af.addRow("API-ключ", self.ai_key_edit)
        self.ai_model_edit = QLineEdit(settings.value("ai_model", "", type=str))
        self.ai_model_edit.setPlaceholderText(ai_fixer.DEFAULT_MODEL if ai_fixer else "")
        af.addRow("Модель", self.ai_model_edit)
        self.ai_enabled = QCheckBox("Разбирать вылеты игры и серверов")
        self.ai_enabled.setChecked(settings.value("ai_enabled", True, type=bool))
        af.addRow("ИИ-починка", self.ai_enabled)
        self.ai_auto_apply = QCheckBox("Применять исправления сам")
        self.ai_auto_apply.setChecked(settings.value("ai_auto_apply", True, type=bool))
        af.addRow("", self.ai_auto_apply)
        self.ai_auto_restart = QCheckBox("Перезапускать после исправления")
        self.ai_auto_restart.setChecked(settings.value("ai_auto_restart", True, type=bool))
        af.addRow("", self.ai_auto_restart)
        self.ai_attempts = QComboBox()
        self.ai_attempts.addItems(["1", "2", "3", "4", "5"])
        self.ai_attempts.setCurrentText(str(settings.value("ai_max_attempts", 3, type=int)))
        af.addRow("Макс. попыток", self.ai_attempts)
        tabs.addTab(ai, "ИИ")

        reset = QWidget()
        rl = QVBoxLayout(reset)
        info = QLabel("Сброс настроек. Инстансы и моды НЕ удаляются.")
        info.setObjectName("subtitle")
        info.setWordWrap(True)
        btn = QPushButton("Сбросить")
        btn.setObjectName("folder")
        btn.clicked.connect(self._reset)
        rl.addWidget(info)
        rl.addWidget(btn)
        rl.addStretch(1)
        tabs.addTab(reset, "Сброс")

        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel)
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("Сохранить")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("Отмена")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)

        root = QVBoxLayout(self)
        root.addWidget(tabs)
        root.addWidget(buttons)

    def _pick_bg(self):
        path, _ = QFileDialog.getOpenFileName(self, "Фон лаунчера", "",
                                              "Картинки (*.png *.jpg *.jpeg *.webp *.bmp)")
        if path:
            self.bg_path = path
            self.bg_box.setCurrentIndex(self.bg_box.findData("image"))

    def _reset(self):
        if QMessageBox.question(self, "Сброс", "Уверен?") != QMessageBox.StandardButton.Yes:
            return
        self.settings.clear()
        QMessageBox.information(self, "Готово", "Настройки сброшены.")

    def apply(self):
        self.settings.setValue("nick", self.nick_edit.text().strip())
        self.settings.setValue("theme", self.theme_box.currentData())
        self.settings.setValue("bg_mode", self.bg_box.currentData())
        self.settings.setValue("bg_image", self.bg_path)
        self.settings.setValue("launch_anim", self.launch_anim.isChecked())
        self.settings.setValue("discord_idle", self.discord_idle.isChecked())
        self.settings.setValue("discord_client_id", self.discord_id_edit.text().strip())
        self.settings.setValue("ai_api_key", self.ai_key_edit.text().strip())
        self.settings.setValue("ai_model", self.ai_model_edit.text().strip())
        self.settings.setValue("ai_enabled", self.ai_enabled.isChecked())
        self.settings.setValue("ai_auto_apply", self.ai_auto_apply.isChecked())
        self.settings.setValue("ai_auto_restart", self.ai_auto_restart.isChecked())
        self.settings.setValue("ai_max_attempts", int(self.ai_attempts.currentText()))


class SkinManagerDialog(QDialog):
    def __init__(self, parent, settings: QSettings, nickname: str, instances: list):
        super().__init__(parent)
        self.settings = settings
        self.nickname = nickname
        self.instances = instances
        self.setWindowTitle(f"Скины — {nickname}")
        self.setMinimumSize(560, 460)

        self.list = QListWidget()
        self.list.setViewMode(QListWidget.ViewMode.IconMode)
        self.list.setIconSize(QSize(72, 96))
        self.list.setGridSize(QSize(100, 132))
        self.list.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.list.setMovement(QListWidget.Movement.Static)
        self.list.setSpacing(6)
        self.list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)

        add_file_btn = QPushButton("Импорт из файла...")
        add_file_btn.setObjectName("small")
        add_file_btn.clicked.connect(self._import_file)
        del_btn = QPushButton("Удалить")
        del_btn.setObjectName("small")
        del_btn.clicked.connect(self._delete_selected)
        top_row = QHBoxLayout()
        top_row.setSpacing(6)
        top_row.addWidget(add_file_btn)
        top_row.addStretch(1)
        top_row.addWidget(del_btn)

        self.fetch_edit = QLineEdit()
        self.fetch_edit.setPlaceholderText("Ник игрока с лицензией (Mojang)")
        self.fetch_btn = QPushButton("Скачать по нику")
        self.fetch_btn.setObjectName("small")
        self.fetch_btn.clicked.connect(self._fetch_by_name)
        fetch_row = QHBoxLayout()
        fetch_row.setSpacing(6)
        fetch_row.addWidget(self.fetch_edit, 1)
        fetch_row.addWidget(self.fetch_btn)

        self.active_btn = QPushButton(f"Сделать активным для «{nickname}»")
        self.active_btn.clicked.connect(self._set_active)
        self.export_btn = QPushButton("Применить к инстансам")
        self.export_btn.setObjectName("folder")
        self.export_btn.clicked.connect(self._export_to_instances)
        bottom_row = QHBoxLayout()
        bottom_row.setSpacing(6)
        bottom_row.addWidget(self.active_btn, 1)
        bottom_row.addWidget(self.export_btn)

        hint = QLabel(
            "Это офлайн-ники — сам Minecraft без лицензии скин не покажет. "
            "«Применить к инстансам» кладёт скин в локальную папку мода "
            "CustomSkinLoader (режим Local Skin) — поставь этот мод в инстанс, "
            "чтобы скин был виден в игре и друзьям на твоём сервере."
        )
        hint.setObjectName("subtitle")
        hint.setWordWrap(True)

        close_btn = QPushButton("Закрыть")
        close_btn.clicked.connect(self.accept)

        root = QVBoxLayout(self)
        root.addLayout(top_row)
        root.addWidget(self.list, 1)
        root.addLayout(fetch_row)
        root.addLayout(bottom_row)
        root.addWidget(hint)
        root.addWidget(close_btn)

        self._reload()

    def _reload(self):
        self.list.clear()
        active_id = self.settings.value(f"skin_active_{self.nickname}", "", type=str)
        for entry in load_skins_index():
            path = SKINS_DIR / entry["file"]
            if not path.exists():
                continue
            data = path.read_bytes()
            pix = render_skin_preview(data, 96) or letter_icon(entry["name"], 72)
            label = entry["name"] + ("\n★ активен" if entry["id"] == active_id else "")
            item = QListWidgetItem(QIcon(pix), label)
            item.setData(Qt.ItemDataRole.UserRole, entry["id"])
            item.setTextAlignment(Qt.AlignmentFlag.AlignHCenter)
            self.list.addItem(item)

    def _current_entry(self):
        item = self.list.currentItem()
        if not item:
            return None
        sid = item.data(Qt.ItemDataRole.UserRole)
        for entry in load_skins_index():
            if entry.get("id") == sid:
                return entry
        return None

    def _import_file(self):
        path, _ = QFileDialog.getOpenFileName(self, "Выбери PNG со скином", "", "PNG (*.png)")
        if not path:
            return
        data = Path(path).read_bytes()
        pix = QPixmap()
        if not pix.loadFromData(data) or pix.width() != 64 or pix.height() not in (32, 64):
            QMessageBox.warning(self, "Не похоже на скин",
                                "Нужен PNG размером 64×64 (или старый формат 64×32).")
            return
        name, ok = QInputDialog.getText(self, "Имя скина", "Название:",
                                         text=Path(path).stem[:24])
        if not ok:
            return
        add_local_skin(data, name.strip() or "Скин", source="файл")
        self._reload()

    def _fetch_by_name(self):
        name = self.fetch_edit.text().strip()
        if not name:
            return
        self.fetch_btn.setEnabled(False)
        try:
            data = fetch_skin_by_username(name)
        except Exception as e:
            QMessageBox.critical(self, "Не удалось скачать", str(e))
            return
        finally:
            self.fetch_btn.setEnabled(True)
        add_local_skin(data, name, source=f"ник {name}")
        self.fetch_edit.clear()
        self._reload()

    def _delete_selected(self):
        entry = self._current_entry()
        if not entry:
            return
        if QMessageBox.question(self, "Удалить скин", f"Удалить «{entry['name']}»?") \
                != QMessageBox.StandardButton.Yes:
            return
        delete_local_skin(entry["id"])
        self._reload()

    def _set_active(self):
        entry = self._current_entry()
        if not entry:
            QMessageBox.information(self, "Скин не выбран", "Сначала выбери скин в списке.")
            return
        self.settings.setValue(f"skin_active_{self.nickname}", entry["id"])
        self._reload()

    def _export_to_instances(self):
        entry = self._current_entry()
        if not entry:
            QMessageBox.information(self, "Скин не выбран", "Сначала выбери скин в списке.")
            return
        if not self.instances:
            QMessageBox.information(self, "Нет инстансов", "Сначала создай хотя бы один инстанс.")
            return
        data = (SKINS_DIR / entry["file"]).read_bytes()
        written = export_skin_to_customskinloader(self.nickname, data, self.instances)
        QMessageBox.information(
            self, "Готово",
            f"Скин записан в {len(written)} инстанс(ов) как локальный скин "
            f"CustomSkinLoader для ника «{self.nickname}».\n"
            "Проверь, что в моде включён режим Local Skin."
        )


# ===========================================================================
# InstanceTile + grid list
# ===========================================================================
class InstanceTile(QFrame):
    gear_clicked = pyqtSignal(object)
    SIZE = QSize(76, 92)
    ICON = 44

    def __init__(self, inst: Instance, selected: bool):
        super().__init__()
        self.inst = inst
        self.setObjectName("tile")
        self.setProperty("selected", "true" if selected else "false")
        self.setFixedSize(self.SIZE)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setToolTip(f"{inst.name}\n{inst.loader} {inst.mc_version}\n"
                        f"{count_mods(inst.folder)} модов · {inst.ram_mb / 1024:g} GB RAM")

        icon_holder = QWidget()
        icon_holder.setFixedSize(self.ICON + 12, self.ICON + 4)
        icon_lbl = QLabel(icon_holder)
        icon_lbl.setPixmap(loader_badge(inst.loader, self.ICON))
        icon_lbl.setFixedSize(self.ICON, self.ICON)
        icon_lbl.move((icon_holder.width() - self.ICON) // 2, 0)
        icon_lbl.setStyleSheet("background: transparent;")

        dot = QLabel(icon_holder)
        dot.setFixedSize(15, 15)
        bg, fg = LOADER_COLORS.get(inst.loader, ("#3a4150", "#dfe3ea"))
        dot.setStyleSheet(
            f"background: {bg}; color: {fg}; border-radius: 7px; "
            f"font-size: 7px; font-weight: 700; text-align: center;"
        )
        dot.setText({"Vanilla": "V", "Paper": "P", "Fabric": "F", "Quilt": "Q",
                     "Forge": "Fo", "NeoForge": "NF"}.get(inst.loader, "?"))
        dot.setAlignment(Qt.AlignmentFlag.AlignCenter)
        dot.move(icon_holder.width() - 14, self.ICON - 12)

        stats = inst.get_stats()
        played = int(stats.get("seconds", 0)) > 0
        status_dot = QLabel(icon_holder)
        status_dot.setFixedSize(8, 8)
        status_dot.setStyleSheet(
            f"background: {THEMES['dark']['ok'] if played else THEMES['dark']['muted']}; "
            f"border-radius: 4px; border: 1px solid rgba(0,0,0,120);"
        )
        status_dot.setToolTip(
            f"Играл: {fmt_duration(int(stats.get('seconds', 0)))}" if played else "Ещё не запускался"
        )
        status_dot.move(1, 1)

        f = QFont("Segoe UI")
        f.setPixelSize(12)
        name = QLabel(QFontMetrics(f).elidedText(
            inst.name, Qt.TextElideMode.ElideRight, self.SIZE.width() - 10))
        name.setObjectName("tile_name")
        name.setAlignment(Qt.AlignmentFlag.AlignCenter)
        name.setMinimumWidth(1)

        sub = QLabel(inst.mc_version)
        sub.setObjectName("tile_badge")
        sub.setAlignment(Qt.AlignmentFlag.AlignCenter)
        sub.setStyleSheet(f"color: {THEMES['dark']['muted']}; font-size: 9px; background: transparent;")

        v = QVBoxLayout(self)
        v.setContentsMargins(4, 6, 4, 4)
        v.setSpacing(2)
        v.addWidget(icon_holder, 0, Qt.AlignmentFlag.AlignHCenter)
        v.addWidget(name)
        v.addWidget(sub)
        v.addStretch(1)

        self._shadow = QGraphicsDropShadowEffect(self)
        self._shadow.setBlurRadius(0)
        self._shadow.setOffset(0, 0)
        self._shadow.setColor(QColor(0, 0, 0, 0))
        self.setGraphicsEffect(self._shadow)
        self._anim = QPropertyAnimation(self._shadow, b"blurRadius", self)
        self._anim.setDuration(120)

    def enterEvent(self, event):
        self._anim.stop()
        self._anim.setStartValue(self._shadow.blurRadius())
        self._anim.setEndValue(18)
        self._anim.start()
        super().enterEvent(event)

    def leaveEvent(self, event):
        self._anim.stop()
        self._anim.setStartValue(self._shadow.blurRadius())
        self._anim.setEndValue(0)
        self._anim.start()
        super().leaveEvent(event)


def make_grid_list(lst: QListWidget):
    lst.setViewMode(QListView.ViewMode.IconMode)
    lst.setResizeMode(QListView.ResizeMode.Adjust)
    lst.setMovement(QListView.Movement.Static)
    lst.setWrapping(True)
    lst.setUniformItemSizes(True)
    lst.setDragEnabled(False)
    lst.setSpacing(6)


class InstanceDropList(QListWidget):
    files_dropped = pyqtSignal(list)
    context_menu_requested = pyqtSignal(object, QPoint)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAcceptDrops(True)
        self.viewport().setAcceptDrops(True)
        self.setDragDropMode(QAbstractItemView.DragDropMode.DropOnly)
        self.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.customContextMenuRequested.connect(self._menu)

    @staticmethod
    def _mrpack_urls(event):
        if not event.mimeData().hasUrls():
            return []
        return [Path(u.toLocalFile()) for u in event.mimeData().urls()
                if u.isLocalFile() and Path(u.toLocalFile()).suffix.lower() in (".mrpack", ".zip")]

    def dragEnterEvent(self, event):
        if self._mrpack_urls(event):
            event.acceptProposedAction()
        else:
            event.ignore()

    def dragMoveEvent(self, event):
        if self._mrpack_urls(event):
            event.acceptProposedAction()
        else:
            event.ignore()

    def dropEvent(self, event):
        paths = self._mrpack_urls(event)
        if paths:
            event.acceptProposedAction()
            self.files_dropped.emit(paths)
        else:
            event.ignore()

    def _menu(self, pos):
        item = self.itemAt(pos)
        if not item:
            return
        self.setCurrentRow(self.row(item))
        card = self.itemWidget(item)
        if card and hasattr(card, "inst"):
            self.context_menu_requested.emit(card.inst, self.mapToGlobal(pos))


if ai_fixer is not None:
    ai_fixer.configure(
        VERSIONS_DIR=VERSIONS_DIR,
        ModrinthProvider=ModrinthProvider,
        java_major_for_mc=java_major_for_mc,
        build_style=build_style,
    )


# ===========================================================================
# Launch animation
# ===========================================================================
def _clamp01(x: float) -> float:
    return max(0.0, min(1.0, x))


def _ease_out_cubic(x: float) -> float:
    return 1 - (1 - x) ** 3


def _ease_out_back(x: float, s: float = 1.9) -> float:
    x -= 1
    return 1 + (s + 1) * x ** 3 + s * x ** 2


def _make_char_pixmap(u: float, dpr: float, accent: QColor):
    pw, ph = int(46 * u), int(40 * u)
    pm = QPixmap(int(pw * dpr), int(ph * dpr))
    pm.setDevicePixelRatio(dpr)
    pm.fill(Qt.GlobalColor.transparent)
    p = QPainter(pm)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.translate(pw * 0.64, ph - 3 * u)
    p.rotate(13)

    out = QColor(14, 16, 24, 235)
    skin, hair = QColor("#c58c63"), QColor("#3a2616")
    shirt, pants, shoe = QColor("#1fb2b0"), QColor("#34379a"), QColor("#4a4d57")

    def box(x, y, w, h, fill):
        p.setPen(QPen(out, 1.2))
        p.setBrush(QBrush(fill))
        p.drawRect(QRectF(x, y, w, h))

    def limb(px, py, w, segs, ang, dark=False):
        p.save()
        p.translate(px, py)
        p.rotate(ang)
        y = 0.0
        for h, col in segs:
            box(-w / 2, y, w, h, col.darker(140) if dark else col)
            y += h
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor(0, 0, 0, 45))
        p.drawRect(QRectF(w * 0.22, 0, w * 0.28, y))
        p.restore()

    limb(0, -22.5 * u, 4 * u, [(5 * u, shirt), (7 * u, skin)], 72, dark=True)
    limb(0, -12 * u, 4 * u, [(9 * u, pants), (3 * u, shoe)], 42, dark=True)
    box(-4 * u, -24 * u, 8 * u, 12 * u, shirt)
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor(0, 0, 0, 40))
    p.drawRect(QRectF(1.5 * u, -24 * u, 2.5 * u, 12 * u))
    for pts, col in (
        ([(-3, -25.5), (-9, -28.5), (-17, -29), (-22, -26.5), (-15, -25.2), (-8, -24)], accent),
        ([(-3, -24), (-8, -22.5), (-15, -21.5), (-19, -19.5), (-12, -20.2), (-6, -22)], accent.darker(125)),
    ):
        poly = QPolygonF([QPointF(x * u, y * u) for x, y in pts])
        p.setPen(QPen(out, 1.2))
        p.setBrush(col)
        p.drawPolygon(poly)
    limb(0, -12 * u, 4 * u, [(9 * u, pants), (3 * u, shoe)], -38)
    box(-4 * u, -32 * u, 8 * u, 8 * u, skin)
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(hair)
    p.drawRect(QRectF(-4 * u, -32 * u, 8 * u, 2.6 * u))
    p.drawRect(QRectF(-4 * u, -32 * u, 3 * u, 6.2 * u))
    p.setBrush(QColor("white"))
    p.drawRect(QRectF(1.0 * u, -28.6 * u, 2.7 * u, 1.8 * u))
    p.setBrush(QColor("#2a3f8f"))
    p.drawRect(QRectF(2.5 * u, -28.6 * u, 1.3 * u, 1.8 * u))
    p.setBrush(hair)
    p.drawRect(QRectF(0.8 * u, -29.5 * u, 3.1 * u, 0.7 * u))
    p.setBrush(QColor("#a4694a"))
    p.drawRect(QRectF(1.6 * u, -25.8 * u, 2.0 * u, 0.6 * u))
    limb(0, -22.5 * u, 4 * u, [(5 * u, shirt), (7 * u, skin)], -52)
    p.end()
    return pm, pw, ph


class LaunchSplash(QWidget):
    DURATION = 3.4
    RUN_START, RUN_DUR = 0.5, 0.95

    def __init__(self, parent: QWidget, theme: str = "dark"):
        super().__init__(parent)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)
        self.setAttribute(Qt.WidgetAttribute.WA_NoSystemBackground, True)
        pal = THEMES.get(theme, THEMES["dark"])
        self.c_acc = QColor(pal["accent"])
        self.c_light = QColor(pal["accent_light"])
        self.c_dark = QColor(pal["accent_dark"])
        self.setGeometry(parent.rect())
        self._t0 = time.monotonic()
        self._last = 0.0
        self._particles: list = []
        self._landed: set = set()
        self._built_size = None
        self._letters: list = []
        self._tick_timer = QTimer(self)
        self._tick_timer.timeout.connect(self._tick)
        self._tick_timer.start(16)

    def _build(self):
        W, H = self.width(), self.height()
        self._built_size = (W, H)
        self._u = max(3.0, H / 140)
        self._pm, self._pw, self._ph = _make_char_pixmap(
            self._u, self.devicePixelRatioF(), self.c_acc)
        self._gy = H * 0.89

        self._letters = []
        self._all_path = QPainterPath()
        specs = (("Frimer", int(H * 0.17), 0.0, 0.36, 0.25),
                 ("Launcher", int(H * 0.095), 0.04, 0.36 + 0.14, 0.65))
        self._words = []
        for word, px, spacing_k, base_y, t_base in specs:
            f = QFont()
            f.setFamilies(["Bahnschrift", "Segoe UI"])
            f.setPixelSize(px)
            f.setWeight(QFont.Weight.Bold)
            fm = QFontMetricsF(f)
            sp = px * (spacing_k + 0.02)
            advs = [fm.horizontalAdvance(ch) for ch in word]
            total = sum(advs) + sp * (len(word) - 1)
            x = (W - total) / 2
            y = H * base_y + px * 0.75
            for i, ch in enumerate(word):
                path = QPainterPath()
                path.addText(0, 0, f, ch)
                self._letters.append({
                    "ch": ch, "path": path, "x": x, "y": y, "adv": advs[i], "size": px,
                    "start": t_base + i * 0.06, "rot": 24 if i % 2 else -24,
                })
                self._all_path.addPath(path.translated(x, y))
                x += advs[i] + sp
            self._words.append({"y": y, "size": px, "total": total, "cx": W / 2})
        self._tag_font = QFont()
        self._tag_font.setFamilies(["Bahnschrift", "Segoe UI"])
        self._tag_font.setPixelSize(max(11, int(H * 0.028)))
        self._tag_font.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, 3)

    def _char_x(self, t: float):
        p = _clamp01((t - self.RUN_START) / self.RUN_DUR)
        e = p * p * (3 - 2 * p)
        return -self._pw + e * (self.width() + 2 * self._pw), p

    def _add(self, x, y, vx, vy, life, size, color, g=0.0):
        self._particles.append({"x": x, "y": y, "vx": vx, "vy": vy, "life": life,
                                "max": life, "size": size, "color": color, "g": g})

    def _tick(self):
        now = time.monotonic() - self._t0
        dt = min(0.05, max(0.0, now - self._last))
        self._last = now
        par = self.parentWidget()
        if par is not None and self.geometry() != par.rect():
            self.setGeometry(par.rect())
        if self._built_size != (self.width(), self.height()):
            self._build()
        W, H, u = self.width(), self.height(), self._u
        rnd = random.random

        cx, run_p = self._char_x(now)
        if 0 < run_p < 1:
            for _ in range(3):
                col = QColor(self.c_light) if rnd() < 0.3 else QColor(205, 208, 218)
                self._add(cx - self._pw * 0.2 + (rnd() - 0.5) * 12, self._gy - 2,
                          -(120 + rnd() * 160), -(15 + rnd() * 80), 0.45 + rnd() * 0.4,
                          u * (0.6 + rnd() * 0.9), col, g=120)
        for i, L in enumerate(self._letters):
            if i not in self._landed and now >= L["start"] + 0.21:
                self._landed.add(i)
                bx, by = L["x"] + L["adv"] / 2, L["y"]
                for _ in range(7):
                    a = math.radians(-170 + rnd() * 160)
                    sp = 80 + rnd() * 160
                    col = QColor(255, 255, 255) if rnd() < 0.35 else QColor(self.c_light)
                    self._add(bx, by, math.cos(a) * sp, math.sin(a) * sp, 0.5 + rnd() * 0.4,
                              H * (0.006 + rnd() * 0.006), col, g=420)
        if now < self.DURATION - 0.7 and rnd() < 0.45:
            self._add(rnd() * W, H + 4, (rnd() - 0.5) * 30, -(70 + rnd() * 90),
                      1.4 + rnd() * 0.8, H * (0.004 + rnd() * 0.004),
                      QColor(self.c_acc), g=0)

        alive = []
        for pt in self._particles:
            pt["life"] -= dt
            if pt["life"] <= 0:
                continue
            pt["vy"] += pt["g"] * dt
            pt["x"] += pt["vx"] * dt
            pt["y"] += pt["vy"] * dt
            alive.append(pt)
        self._particles = alive

        self.update()
        if now >= self.DURATION:
            self._tick_timer.stop()
            self.hide()
            self.deleteLater()

    def paintEvent(self, _ev):
        if self._built_size != (self.width(), self.height()):
            self._build()
        t = time.monotonic() - self._t0
        W, H, u = self.width(), self.height(), self._u
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.setRenderHint(QPainter.RenderHint.TextAntialiasing)
        fade_in = _clamp01(t / 0.3)
        master = 1 - _clamp01((t - (self.DURATION - 0.6)) / 0.6)

        scrim = QColor(6, 8, 12)
        scrim.setAlpha(int(170 * fade_in * master))
        p.fillRect(self.rect(), scrim)
        glow = QRadialGradient(QPointF(W / 2, H * 0.42), max(W, H) * 0.55)
        g0 = QColor(self.c_acc)
        g0.setAlpha(int(55 * fade_in * master))
        g1 = QColor(self.c_acc)
        g1.setAlpha(0)
        glow.setColorAt(0, g0)
        glow.setColorAt(1, g1)
        p.fillRect(self.rect(), QBrush(glow))

        p.setOpacity(master)
        gy = self._gy
        fl = QLinearGradient(0, 0, W, 0)
        c0, c1 = QColor(self.c_acc), QColor(self.c_acc)
        c0.setAlpha(0)
        c1.setAlpha(int(150 * fade_in))
        fl.setColorAt(0, c0)
        fl.setColorAt(0.5, c1)
        fl.setColorAt(1, c0)
        p.setPen(QPen(QBrush(fl), 2))
        p.drawLine(QPointF(0, gy), QPointF(W, gy))

        cx, run_p = self._char_x(t)
        if 0 < run_p < 1:
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor(0, 0, 0, 90))
            p.drawEllipse(QPointF(cx - u * 3, gy + 1), u * 10, u * 1.6)
            for i in range(8):
                ly = gy - u * (2.5 + i * 3.6)
                ln = u * (16 + (i * 7) % 5 * 7)
                lx1 = cx - self._pw * 0.28 - (i % 3) * u * 4
                lg = QLinearGradient(lx1 - ln, 0, lx1, 0)
                a0, a1 = QColor(self.c_light), QColor(255, 255, 255)
                a0.setAlpha(0)
                a1.setAlpha(170)
                lg.setColorAt(0, a0)
                lg.setColorAt(1, a1)
                p.setPen(QPen(QBrush(lg), max(1.5, u * 0.45), Qt.PenStyle.SolidLine,
                              Qt.PenCapStyle.RoundCap))
                p.drawLine(QPointF(lx1 - ln, ly), QPointF(lx1, ly))
            px0, py0 = cx - self._pw * 0.64, gy - (self._ph - 3 * u)
            for k in (4, 3, 2, 1):
                p.setOpacity(master * 0.30 / k)
                p.drawPixmap(QPointF(px0 - k * u * 6.5, py0), self._pm)
            p.setOpacity(master)
            p.drawPixmap(QPointF(px0, py0), self._pm)

        p.setPen(Qt.PenStyle.NoPen)
        for pt in self._particles:
            f = pt["life"] / pt["max"]
            s = pt["size"] * (0.4 + 0.6 * f)
            p.setOpacity(master * min(1.0, f * 1.6))
            p.fillRect(QRectF(pt["x"] - s / 2, pt["y"] - s / 2, s, s), pt["color"])
        p.setOpacity(master)

        for L in self._letters:
            pr = _clamp01((t - L["start"]) / 0.6)
            if pr <= 0:
                continue
            e = _ease_out_back(pr)
            yoff = -(1 - e) * (L["y"] + L["size"] * 1.2)
            size = L["size"]
            p.save()
            p.translate(L["x"] + L["adv"] / 2, L["y"] + yoff - size * 0.3)
            p.rotate((1 - pr) * L["rot"])
            p.translate(-L["adv"] / 2, size * 0.3)
            p.setOpacity(master * min(1.0, pr * 5))
            path = L["path"]
            p.setBrush(Qt.BrushStyle.NoBrush)
            for w, a in ((size * 0.16, 28), (size * 0.09, 55)):
                gc = QColor(self.c_acc)
                gc.setAlpha(a)
                pen = QPen(gc, w)
                pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
                p.setPen(pen)
                p.drawPath(path)
            fill = QLinearGradient(0, -size * 0.75, 0, size * 0.02)
            fill.setColorAt(0, QColor("#fff1c9"))
            fill.setColorAt(0.35, self.c_light)
            fill.setColorAt(1, self.c_dark)
            pen = QPen(QColor(28, 16, 4, 230), max(1.5, size * 0.035))
            pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
            p.setPen(pen)
            p.setBrush(QBrush(fill))
            p.drawPath(path)
            p.restore()
        p.setOpacity(master)

        sh = (t - 1.85) / 0.7
        if 0 < sh < 1:
            p.save()
            p.setClipPath(self._all_path)
            top = self._words[0]["y"] - self._words[0]["size"]
            bx = -W * 0.1 + sh * W * 1.2
            bg = QLinearGradient(QPointF(bx - 70, top), QPointF(bx + 70, top + 90))
            w0, w1 = QColor(255, 255, 255, 0), QColor(255, 255, 255, 210)
            bg.setColorAt(0, w0)
            bg.setColorAt(0.5, w1)
            bg.setColorAt(1, w0)
            p.fillRect(self.rect(), QBrush(bg))
            p.restore()

        w2 = self._words[1]
        pu = _ease_out_cubic(_clamp01((t - 1.5) / 0.5))
        if pu > 0:
            yl = w2["y"] + w2["size"] * 0.38
            half = w2["total"] * 0.6 * pu
            ug = QLinearGradient(w2["cx"] - half, 0, w2["cx"] + half, 0)
            u0, u1 = QColor(self.c_acc), QColor(self.c_light)
            u0.setAlpha(0)
            ug.setColorAt(0, u0)
            ug.setColorAt(0.5, u1)
            ug.setColorAt(1, u0)
            p.setPen(QPen(QBrush(ug), max(2.0, H * 0.006), Qt.PenStyle.SolidLine,
                          Qt.PenCapStyle.RoundCap))
            p.drawLine(QPointF(w2["cx"] - half, yl), QPointF(w2["cx"] + half, yl))
            ta = _clamp01((t - 1.7) / 0.4)
            if ta > 0:
                p.setFont(self._tag_font)
                txt = "ЗАПУСК ИГРЫ" + "." * (int(t * 3) % 4)
                fm = QFontMetricsF(self._tag_font)
                full = fm.horizontalAdvance("ЗАПУСК ИГРЫ...") + 3 * 14
                col = QColor(MUTED_TEXT)
                col.setAlpha(int(230 * ta))
                p.setPen(col)
                p.drawText(QPointF(w2["cx"] - full / 2, yl + H * 0.065), txt)
        p.end()


def _draw_paw(p: QPainter, x: float, y: float, size: float, angle: float, color: QColor):
    p.save()
    p.translate(x, y)
    p.rotate(angle)
    p.scale(size, size)
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(color)
    p.drawEllipse(QPointF(0, 0.20), 0.30, 0.24)
    for cx, cy, rx, ry, a in ((-0.38, -0.08, 0.10, 0.15, -25), (-0.14, -0.32, 0.10, 0.16, -8),
                              (0.14, -0.32, 0.10, 0.16, 8), (0.38, -0.08, 0.10, 0.15, 25)):
        p.save()
        p.translate(cx, cy)
        p.rotate(a)
        p.drawEllipse(QPointF(0, 0), rx, ry)
        p.restore()
    p.restore()


def paint_app_background(p: QPainter, w: int, h: int, theme: str, image=None):
    t = THEMES.get(theme, THEMES["dark"])
    if image is not None and not image.isNull():
        p.drawPixmap(0, 0, image)
        p.fillRect(0, 0, w, h, QColor(0, 0, 0, 150) if theme == "dark" else QColor(255, 255, 255, 150))
        return
    grad = QLinearGradient(0, 0, w * 0.4, h)
    if theme == "dark":
        grad.setColorAt(0.0, QColor("#1a1220"))
        grad.setColorAt(0.55, QColor("#241823"))
        grad.setColorAt(1.0, QColor("#2b1d1c"))
    else:
        grad.setColorAt(0.0, QColor("#fbf1e8"))
        grad.setColorAt(1.0, QColor("#f2dcc8"))
    p.fillRect(0, 0, w, h, QBrush(grad))
    paw = QColor(t["accent"])
    paw.setAlpha(22 if theme == "dark" else 34)
    step = 128
    for j in range(-1, h // step + 2):
        for i in range(-1, w // step + 2):
            x = i * step + (step // 2 if j % 2 else 0) + ((i * 37 + j * 53) % 29) - 14
            y = j * step + ((i * 71 + j * 19) % 31) - 15
            angle = ((i * 47 + j * 83) % 70) - 35
            _draw_paw(p, x, y, 30 + (i * 13 + j * 7) % 9, angle, paw)


# ===========================================================================
# Launcher
# ===========================================================================
class Launcher(QWidget):
    def __init__(self):
        super().__init__()
        self.setObjectName("root")
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
        self.setWindowTitle("Frimer")
        self.resize(960, 640)
        self.setMinimumSize(840, 560)

        self.settings = QSettings("Frimer", "Frimer")
        self._launcher_started = int(time.time())
        self._game_started_ts = self._launcher_started
        self._playing_inst = None
        self._import_queue: list = []
        self._import_worker = None
        self._crash_dlg = None
        self.discord = DiscordPresence(self._discord_client_id())
        self.instances: list = []
        self.current = None
        self.tools_window = None
        self.launch_worker = None
        self.current_nick = self.settings.value("nick", "Player")
        self.nick_history = self._load_history()

        self.servers: list = []
        self.current_server = None
        self.server_runner = None
        self.server_console = None
        self.server_install_worker = None
        self.server_tab_active = False

        self._ai_attempts: dict = {}
        self._ai_history: dict = {}
        self._ai_busy: set = set()
        self._ai_retry = False
        self._ai_dialog = None
        self._server_log = deque(maxlen=800)
        self._running_server = None
        self._server_stop_requested = False
        self._server_started_at = 0.0

        self._bg_cache = None
        self.setStyleSheet(self._style())
        if ICON_PATH.exists():
            self.setWindowIcon(QIcon(str(ICON_PATH)))

        title = QLabel("Frimer")
        title.setObjectName("title")

        self.nick_btn = QPushButton()
        self.nick_btn.setObjectName("nick")
        self.nick_btn.clicked.connect(self._nick_menu)
        self._refresh_nick()

        self.skins_btn = QPushButton("\U0001F9CD")
        self.skins_btn.setObjectName("gear")
        self.skins_btn.setFixedSize(30, 30)
        self.skins_btn.setToolTip("Скины")
        self.skins_btn.clicked.connect(self.open_skin_manager)

        self.open_dir_btn = QPushButton("\U0001F4C1")
        self.open_dir_btn.setObjectName("gear")
        self.open_dir_btn.setFixedSize(30, 30)
        self.open_dir_btn.setToolTip("Открыть папку выбранной сборки")
        self.open_dir_btn.clicked.connect(self.open_current_folder)

        nick_row = QHBoxLayout()
        nick_row.setSpacing(8)
        nick_row.addWidget(self.nick_btn, 0)
        nick_row.addWidget(self.skins_btn, 0)
        nick_row.addWidget(self.open_dir_btn, 0)
        nick_row.addStretch(1)

        self.tab_inst_btn = QPushButton("Инстансы")
        self.tab_inst_btn.setObjectName("mini")
        self.tab_inst_btn.setCheckable(True)
        self.tab_inst_btn.setChecked(True)
        self.tab_inst_btn.clicked.connect(lambda: self._switch_tab(False))

        self.tab_srv_btn = QPushButton("Серверы")
        self.tab_srv_btn.setObjectName("mini")
        self.tab_srv_btn.setCheckable(True)
        self.tab_srv_btn.clicked.connect(lambda: self._switch_tab(True))

        tab_row = QHBoxLayout()
        tab_row.setSpacing(6)
        tab_row.addWidget(self.tab_inst_btn)
        tab_row.addWidget(self.tab_srv_btn)
        tab_row.addStretch(1)

        self.inst_list = InstanceDropList()
        self.inst_list.setObjectName("instances")
        make_grid_list(self.inst_list)
        self.inst_list.setSpacing(4)
        self.inst_list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.inst_list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.inst_list.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.inst_list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.inst_list.currentRowChanged.connect(self._on_select)
        self.inst_list.files_dropped.connect(self._on_files_dropped)
        self.inst_list.context_menu_requested.connect(self._context_menu)

        self.srv_list = QListWidget()
        self.srv_list.setObjectName("instances")
        make_grid_list(self.srv_list)
        self.srv_list.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.srv_list.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.srv_list.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.srv_list.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.srv_list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.srv_list.customContextMenuRequested.connect(self._server_menu)
        self.srv_list.setVisible(False)

        self.new_inst_btn = QPushButton("Создать")
        self.new_inst_btn.setObjectName("mini")
        self.new_inst_btn.clicked.connect(self.create_instance)

        self.del_inst_btn = QPushButton("Удалить")
        self.del_inst_btn.setObjectName("mini")
        self.del_inst_btn.clicked.connect(self.delete_current_instance)

        self.settings_btn = QPushButton("Настройки")
        self.settings_btn.setObjectName("mini")
        self.settings_btn.clicked.connect(self.open_settings)

        row1 = QHBoxLayout()
        row1.setSpacing(6)
        row1.addWidget(self.new_inst_btn)
        row1.addWidget(self.del_inst_btn)
        row1.addWidget(self.settings_btn)
        row1.addStretch(1)

        self.new_srv_btn = QPushButton("Создать сервер")
        self.new_srv_btn.setObjectName("mini")
        self.new_srv_btn.clicked.connect(self.create_server)
        self.new_srv_btn.setVisible(False)

        self.del_srv_btn = QPushButton("Удалить сервер")
        self.del_srv_btn.setObjectName("mini")
        self.del_srv_btn.clicked.connect(self.delete_current_server)
        self.del_srv_btn.setVisible(False)

        srv_row1 = QHBoxLayout()
        srv_row1.setSpacing(6)
        srv_row1.addWidget(self.new_srv_btn)
        srv_row1.addWidget(self.del_srv_btn)
        srv_row1.addStretch(1)

        sidebar = QFrame()
        sidebar.setObjectName("sidebar")
        sidebar.setFixedWidth(300)
        side = QVBoxLayout(sidebar)
        side.setContentsMargins(20, 20, 20, 20)
        side.setSpacing(10)
        side.addWidget(title)
        side.addSpacing(10)
        side.addLayout(nick_row)
        side.addSpacing(6)
        side.addLayout(tab_row)
        self.tab_label = field_label("Инстансы")
        side.addWidget(self.tab_label)
        side.addLayout(row1)
        side.addLayout(srv_row1)
        side.addStretch(1)

        self.inst_title = QLabel("Инстанс не выбран")
        self.inst_title.setObjectName("status")
        self.inst_sub = QLabel("Создай инстанс кнопкой «Создать»")
        self.inst_sub.setObjectName("subtitle")

        self.hero = QFrame()
        self.hero.setObjectName("hero")
        self.hero.setFixedHeight(96)
        self.hero.setVisible(False)

        self.hero_icon = QLabel()
        self.hero_icon.setFixedSize(64, 64)
        self.hero_icon.setStyleSheet("background: transparent;")

        self.hero_title = QLabel("")
        self.hero_title.setObjectName("hero_title")
        self.hero_sub = QLabel("")
        self.hero_sub.setObjectName("hero_sub")
        self.hero_stat = QLabel("")
        self.hero_stat.setObjectName("hero_stat")

        hero_text = QVBoxLayout()
        hero_text.setSpacing(2)
        hero_text.addWidget(self.hero_title)
        hero_text.addWidget(self.hero_sub)
        hero_text.addWidget(self.hero_stat)

        self.hero_play = QPushButton("Играть")
        self.hero_play.setObjectName("play")
        self.hero_play.setFixedSize(140, 48)
        self.hero_play.clicked.connect(self.play)
        self.hero_play.setEnabled(False)

        hh = QHBoxLayout(self.hero)
        hh.setContentsMargins(16, 12, 16, 12)
        hh.setSpacing(16)
        hh.addWidget(self.hero_icon, 0, Qt.AlignmentFlag.AlignVCenter)
        hh.addLayout(hero_text, 1)
        hh.addWidget(self.hero_play, 0, Qt.AlignmentFlag.AlignVCenter)

        head = QVBoxLayout()
        head.setSpacing(8)
        head.addWidget(self.hero)
        head.addWidget(self.inst_title)
        head.addWidget(self.inst_sub)

        self.bar = QProgressBar()
        self.bar.setTextVisible(False)
        self.bar.setMaximum(1)
        self.progress_label = QLabel("")
        self.progress_label.setObjectName("subtitle")
        self.status = QLabel("Готов")
        self.status.setObjectName("subtitle")

        self.play_btn = QPushButton("Играть")
        self.play_btn.setObjectName("play")
        self.play_btn.clicked.connect(self.play)
        self.play_btn.setEnabled(False)

        self.kill_btn = QPushButton("Принудительно закрыть Minecraft")
        self.kill_btn.setObjectName("folder")
        self.kill_btn.clicked.connect(self.force_close_game)
        self.kill_btn.setVisible(False)

        self.start_srv_btn = QPushButton("Запустить сервер")
        self.start_srv_btn.setObjectName("play")
        self.start_srv_btn.clicked.connect(self.start_server)
        self.start_srv_btn.setEnabled(False)
        self.start_srv_btn.setVisible(False)

        self.stop_srv_btn = QPushButton("Остановить сервер")
        self.stop_srv_btn.setObjectName("play")
        self.stop_srv_btn.clicked.connect(self.stop_server)
        self.stop_srv_btn.setEnabled(False)
        self.stop_srv_btn.setVisible(False)

        self.console_srv_btn = QPushButton("Открыть консоль")
        self.console_srv_btn.setObjectName("folder")
        self.console_srv_btn.clicked.connect(self.open_server_console)
        self.console_srv_btn.setEnabled(False)
        self.console_srv_btn.setVisible(False)

        self.copy_addr_btn = QPushButton("Копировать адрес для друзей")
        self.copy_addr_btn.setObjectName("folder")
        self.copy_addr_btn.clicked.connect(self.copy_server_address)
        self.copy_addr_btn.setEnabled(False)
        self.copy_addr_btn.setVisible(False)

        row_status = QHBoxLayout()
        row_status.addWidget(self.status, 1)
        row_status.addWidget(self.progress_label, 0)

        content = QWidget()
        right = QVBoxLayout(content)
        right.setContentsMargins(28, 24, 28, 24)
        right.setSpacing(12)
        right.addLayout(head)
        right.addWidget(self.bar)
        right.addLayout(row_status)
        right.addWidget(self.inst_list, 1)
        right.addWidget(self.srv_list, 1)
        for b in (self.play_btn, self.start_srv_btn, self.stop_srv_btn):
            b.setMinimumWidth(220)
        action_row = QHBoxLayout()
        action_row.setSpacing(8)
        for b in (self.play_btn, self.kill_btn, self.start_srv_btn, self.stop_srv_btn,
                  self.console_srv_btn, self.copy_addr_btn):
            action_row.addWidget(b)
        action_row.addStretch(1)
        self.play_btn.setVisible(False)
        right.addLayout(action_row)

        root = QHBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        root.addWidget(sidebar)
        root.addWidget(content, 1)

        self.refresh_instances()
        self.refresh_servers()
        self._switch_tab(False)
        self.discord.start()

        QTimer.singleShot(300, self._show_changelog_if_new)
        QTimer.singleShot(1500, self._check_for_update)

    def _show_changelog_if_new(self):
        last_seen = self.settings.value("last_seen_version", "", type=str)
        if last_seen == APP_VERSION:
            return
        notes = CHANGELOG.get(APP_VERSION)
        if notes:
            text = "\n".join(f"- {line}" for line in notes)
            QMessageBox.information(self, f"Frimer {APP_VERSION} - что нового", text)
        self.settings.setValue("last_seen_version", APP_VERSION)

    def _check_for_update(self):
        self._update_worker = LauncherUpdateCheckWorker()
        self._update_worker.found.connect(self._on_update_found)
        self._update_worker.start()

    def _on_update_found(self, version: str, url: str, notes: str):
        text = f"Доступна версия {version} (у тебя {APP_VERSION})."
        if notes:
            text += f"\n\n{notes}"
        if url:
            text += f"\n\nСкачать: {url}"
        QMessageBox.information(self, "Обновление Frimer", text)

    def _discord_client_id(self) -> str:
        return (self.settings.value("discord_client_id", "", type=str) or DISCORD_CLIENT_ID).strip()

    def _update_presence(self):
        if self._playing_inst is not None:
            return
        if not self.settings.value("discord_idle", True, type=bool):
            self.discord.set_presence()
            return
        p = {"details": "В лаунчере", "start": self._launcher_started}
        if self.current:
            p["state"] = f"Сборка: {self.current.name}"
        if DISCORD_LARGE_IMAGE:
            p["large_image"] = DISCORD_LARGE_IMAGE
        self.discord.set_presence(**p)

    def _on_game_started(self, inst, started_ts):
        self._playing_inst = inst
        self._game_started_ts = started_ts
        self.kill_btn.setVisible(not self.server_tab_active)
        self.kill_btn.setEnabled(True)
        p = {"details": "Играет в Minecraft",
             "state": f"{inst.name} - {inst.loader} {inst.mc_version}",
             "start": started_ts}
        if DISCORD_LARGE_IMAGE:
            p["large_image"] = DISCORD_LARGE_IMAGE
        self.discord.set_presence(**p)

    def _restart_discord(self):
        new_id = self._discord_client_id()
        if new_id != self.discord.client_id:
            self.discord.stop()
            self.discord.wait(2000)
            self.discord = DiscordPresence(new_id)
            self.discord.start()
            if self._playing_inst is not None:
                self._on_game_started(self._playing_inst, self._game_started_ts)
        self._update_presence()

    def closeEvent(self, event):
        if self.server_runner is not None and self.server_runner.isRunning():
            r = QMessageBox.question(
                self, "Сервер работает",
                "Сервер ещё запущен. Остановить его и выйти?\n"
                "Если «Нет» — окно лаунчера закроется, а сервер продолжит работать."
            )
            if r == QMessageBox.StandardButton.Yes:
                self.server_runner.stop()
                self.server_runner.wait(10000)
        try:
            self.discord.stop()
            self.discord.wait(2000)
        except Exception:
            pass
        super().closeEvent(event)

    def _load_history(self):
        try:
            return json.loads(self.settings.value("nickname_history", "[]"))
        except Exception:
            return []

    def _add_nick(self, n):
        n = n.strip()
        if not n:
            return
        if n in self.nick_history:
            self.nick_history.remove(n)
        self.nick_history.insert(0, n)
        self.nick_history = self.nick_history[:10]
        self.settings.setValue("nickname_history", json.dumps(self.nick_history))

    def _refresh_nick(self):
        self.nick_btn.setText(f"{self.current_nick}  \u25be")

    def _nick_menu(self):
        menu = QMenu(self)
        for nick in self.nick_history:
            a = menu.addAction(nick)
            a.triggered.connect(lambda _=False, n=nick: self._pick_nick(n))
        if self.nick_history:
            menu.addSeparator()
        add = menu.addAction("+ Добавить аккаунт")
        add.triggered.connect(self._new_nick)
        menu.exec(self.nick_btn.mapToGlobal(QPoint(0, self.nick_btn.height())))

    def _pick_nick(self, n):
        self.current_nick = n
        self.settings.setValue("nick", n)
        self._add_nick(n)
        self._refresh_nick()

    def _new_nick(self):
        n, ok = QInputDialog.getText(self, "Новый аккаунт", "Ник:")
        if not ok or not n.strip():
            return
        n = n.strip()[:16]
        if not re.match(r"^[A-Za-z0-9_]+$", n):
            QMessageBox.warning(self, "Неверный ник", "Только буквы, цифры и _ (до 16).")
            return
        self._pick_nick(n)

    def _switch_tab(self, to_servers: bool):
        self.server_tab_active = to_servers
        self.tab_inst_btn.setChecked(not to_servers)
        self.tab_srv_btn.setChecked(to_servers)
        self.tab_label.setText("Серверы" if to_servers else "Инстансы")

        self.inst_list.setVisible(not to_servers)
        self.srv_list.setVisible(to_servers)
        self.new_inst_btn.setVisible(not to_servers)
        self.del_inst_btn.setVisible(not to_servers)
        self.settings_btn.setVisible(not to_servers)
        self.new_srv_btn.setVisible(to_servers)
        self.del_srv_btn.setVisible(to_servers)

        self.play_btn.setVisible(False)
        self.hero.setVisible(not to_servers and self.current is not None)
        self.kill_btn.setVisible(not to_servers and self._playing_inst is not None)
        self.start_srv_btn.setVisible(to_servers)
        self.stop_srv_btn.setVisible(to_servers)
        self.console_srv_btn.setVisible(to_servers)
        self.copy_addr_btn.setVisible(to_servers)

        if to_servers:
            if self.servers:
                self._on_server_select(max(0, self.srv_list.currentRow()))
            else:
                self._on_server_select(-1)
        else:
            if self.instances:
                self._on_select(max(0, self.inst_list.currentRow()))
            else:
                self._on_select(-1)

    def refresh_instances(self):
        self.instances = load_all_instances()
        cur_name = self.current.name if self.current else None
        self.inst_list.blockSignals(True)
        self.inst_list.clear()
        self.current = None
        for i, inst in enumerate(self.instances):
            item = QListWidgetItem()
            item.setSizeHint(InstanceTile.SIZE)
            self.inst_list.addItem(item)
            card = InstanceTile(inst, False)
            card.gear_clicked.connect(self._open_tools_for)
            self.inst_list.setItemWidget(item, card)
            if inst.name == cur_name:
                self.inst_list.setCurrentRow(i)
        self.inst_list.blockSignals(False)
        if self.instances and self.inst_list.currentRow() < 0:
            self.inst_list.setCurrentRow(0)
        else:
            self._on_select(self.inst_list.currentRow())

    def _on_select(self, row: int):
        if self.server_tab_active:
            return
        for i in range(self.inst_list.count()):
            item = self.inst_list.item(i)
            card = self.inst_list.itemWidget(item)
            if card:
                card.setProperty("selected", "true" if i == row else "false")
                card.style().unpolish(card)
                card.style().polish(card)
        if row < 0 or row >= len(self.instances):
            self.current = None
            self.inst_title.setText("Инстанс не выбран")
            self.inst_sub.setText("Создай инстанс кнопкой «Создать»")
            self.play_btn.setEnabled(False)
            self.hero.setVisible(False)
            self._update_presence()
            return
        self.current = self.instances[row]
        inst = self.current
        self.inst_title.setText(inst.name)
        s = inst.get_stats()
        self.inst_sub.setText(
            f"{inst.loader} {inst.mc_version} — "
            f"{inst.ram_mb / 1024:g} GB RAM — "
            f"{count_mods(inst.folder)} модов — "
            f"играл: {fmt_duration(int(s.get('seconds', 0)))}"
        )
        self.play_btn.setEnabled(True)
        self.status.setText("Готов")

        self.hero_icon.setPixmap(loader_badge(inst.loader, 64))
        self.hero_title.setText(inst.name)
        self.hero_sub.setText(f"{inst.loader} {inst.mc_version}")
        stat_bits = [f"{inst.ram_mb / 1024:g} GB", f"{count_mods(inst.folder)} модов"]
        if s.get("last"):
            stat_bits.append(f"последний запуск: {s['last']}")
        self.hero_stat.setText(" · ".join(stat_bits))
        self.hero.setVisible(not self.server_tab_active)
        self.hero_play.setEnabled(self.play_btn.isEnabled())

        self._update_presence()

    def _find(self, inst):
        for i, x in enumerate(self.instances):
            if x.folder == inst.folder:
                return i
        return -1

    def open_current_folder(self):
        target = None
        if self.server_tab_active:
            target = self.current_server.folder if self.current_server else None
        elif self.current:
            target = self.current.folder
        if target and Path(target).exists():
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(target)))
        else:
            self.status.setText("Сначала выбери сборку")

    def _context_menu(self, inst, pos):
        i = self._find(inst)
        if i >= 0:
            self.inst_list.setCurrentRow(i)
        menu = QMenu(self)
        menu.addAction("Играть")
        menu.addSeparator()
        menu.addAction("Моды")
        menu.addAction("Ветка модов")
        menu.addAction("Шкала игры")
        menu.addAction("Шейдеры")
        menu.addAction("Ресурспаки")
        menu.addAction("Миры")
        menu.addAction("Скрины")
        menu.addAction("Загрузчик")
        menu.addSeparator()
        menu.addAction("Изменить")
        menu.addAction("Дублировать")
        menu.addAction("Открыть папку")
        menu.addSeparator()
        menu.addAction("Удалить")
        act = menu.exec(pos)
        if not act:
            return
        t = act.text()
        if t == "Играть":
            self.play()
        elif t == "Моды":
            self._open_tools_for(inst, "mods")
        elif t == "Ветка модов":
            self._open_tools_for(inst, "modtree")
        elif t == "Шкала игры":
            self._open_tools_for(inst, "perf")
        elif t == "Шейдеры":
            self._open_tools_for(inst, "shaders")
        elif t == "Ресурспаки":
            self._open_tools_for(inst, "resourcepacks")
        elif t == "Миры":
            self._open_tools_for(inst, "worlds")
        elif t == "Скрины":
            self._open_tools_for(inst, "screenshots")
        elif t == "Загрузчик":
            self._open_tools_for(inst, "loader")
        elif t == "Изменить":
            self.edit_instance(inst)
        elif t == "Дублировать":
            self.duplicate_instance(inst)
        elif t == "Открыть папку":
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(inst.folder)))
        elif t == "Удалить":
            self.delete_instance(inst)

    def _open_tools_for(self, inst, tab="logs"):
        i = self._find(inst)
        if i >= 0:
            self.inst_list.setCurrentRow(i)
            self.current = self.instances[i]
        if not self.current:
            return
        if self.tools_window:
            self.tools_window.close()
        self.tools_window = ToolsDialog(self, self.current, self, start_tab=tab)
        self.tools_window.show()

    def _on_files_dropped(self, paths):
        if self._import_worker is not None and self._import_worker.isRunning():
            QMessageBox.information(self, "Импорт", "Импорт уже идёт.")
            return
        self._import_queue = [Path(p) for p in paths]
        self._import_next()

    def _import_next(self):
        if not self._import_queue:
            return
        path = self._import_queue.pop(0)
        self.status.setText(f"Импорт: {path.name}...")
        w = ImportWorker(path)
        w.status.connect(self.status.setText)
        w.progress.connect(self._progress)
        w.done.connect(self._on_import_done)
        w.failed.connect(self._on_import_failed)
        self._import_worker = w
        spawn(w)

    def _on_import_reset(self):
        self.bar.setMaximum(1)
        self.bar.setValue(0)
        self.progress_label.setText("")

    def _on_import_done(self, name, folder, warns):
        self._on_import_reset()
        self.status.setText(f"Импортировано: {name}")
        self.refresh_instances()
        for i, x in enumerate(self.instances):
            if str(x.folder) == folder:
                self.inst_list.setCurrentRow(i)
                break
        if warns:
            shown = "\n".join(warns[:8])
            QMessageBox.warning(self, "Импорт с предупреждениями",
                                f"Сборка \"{name}\" добавлена, но часть файлов не скачалась:\n\n{shown}")
        self._import_next()

    def _on_import_failed(self, filename, message):
        self._on_import_reset()
        self.status.setText("Ошибка импорта")
        QMessageBox.critical(self, "Не удалось импортировать", f"{filename}\n\n{message}")
        self._import_next()

    def create_instance(self):
        dlg = NewInstanceDialog(self)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        v = dlg.values()
        if not v["mc_version"]:
            QMessageBox.warning(self, "Нет версии", "Список версий ещё не загружен.")
            return
        base = slugify(v["name"])
        folder = INSTANCES_DIR / base
        n = 2
        while folder.exists():
            folder = INSTANCES_DIR / f"{base}-{n}"
            n += 1
        inst = Instance(folder, v["name"], v["loader"], v["mc_version"],
                        v["ram_mb"], v["loader_version"])
        inst.save()
        for sub in ("mods", "shaderpacks", "resourcepacks", "saves", "screenshots", "crash-reports"):
            (folder / sub).mkdir(parents=True, exist_ok=True)
        self.refresh_instances()

    def edit_instance(self, inst):
        dlg = EditInstanceDialog(self, inst)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        v = dlg.values()
        inst.name = v["name"]
        inst.ram_mb = v["ram_mb"]
        inst.save()
        self.refresh_instances()

    def duplicate_instance(self, inst):
        new = duplicate_instance(inst)
        if not new:
            QMessageBox.warning(self, "Ошибка", "Не удалось скопировать")
            return
        self.refresh_instances()

    def delete_instance(self, inst):
        if QMessageBox.question(self, "Удалить",
                                f"Удалить \"{inst.name}\" со всеми модами и мирами?") != QMessageBox.StandardButton.Yes:
            return
        shutil.rmtree(inst.folder, ignore_errors=True)
        if self.current and self.current.folder == inst.folder:
            self.current = None
        if self.tools_window:
            self.tools_window.close()
            self.tools_window = None
        self.refresh_instances()

    def delete_current_instance(self):
        if not self.current:
            QMessageBox.information(self, "Нет инстанса", "Сначала выбери инстанс.")
            return
        self.delete_instance(self.current)

    def _make_cf(self, provider_cls=CurseForgeProvider):
        key = self.settings.value("curseforge_key", "", type=str)
        if not key:
            key, ok = QInputDialog.getText(self, "CurseForge API-ключ",
                                           "Вставь ключ (console.curseforge.com):")
            if not ok or not key.strip():
                return None
            self.settings.setValue("curseforge_key", key.strip())
            key = key.strip()
        return provider_cls(key)

    def _bg_mode(self) -> str:
        return self.settings.value("bg_mode", "furry", type=str)

    def _style(self) -> str:
        return build_style(self.settings.value("theme", "dark"), fancy=self._bg_mode() != "plain")

    def _bg_pixmap(self):
        if self._bg_mode() != "image":
            return None
        path = self.settings.value("bg_image", "", type=str)
        key = (path, self.width(), self.height())
        if self._bg_cache and self._bg_cache[0] == key:
            return self._bg_cache[1]
        src = QPixmap(path) if path and Path(path).exists() else QPixmap()
        if src.isNull():
            return None
        scaled = src.scaled(self.size(), Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                            Qt.TransformationMode.SmoothTransformation)
        x, y = (scaled.width() - self.width()) // 2, (scaled.height() - self.height()) // 2
        out = scaled.copy(x, y, self.width(), self.height())
        self._bg_cache = (key, out)
        return out

    def paintEvent(self, ev):
        if self._bg_mode() == "plain":
            super().paintEvent(ev)
            return
        p = QPainter(self)
        paint_app_background(p, self.width(), self.height(),
                             self.settings.value("theme", "dark"), self._bg_pixmap())
        p.end()

    def open_settings(self):
        dlg = SettingsDialog(self, self.settings)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        dlg.apply()
        self._bg_cache = None
        self.setStyleSheet(self._style())
        self.update()
        self.current_nick = self.settings.value("nick", "Player")
        self._refresh_nick()
        self._restart_discord()
        if self.tools_window:
            self.tools_window.setStyleSheet(build_style(self.settings.value("theme", "dark")))

    def open_skin_manager(self):
        nick = self.current_nick.strip() or "Player"
        dlg = SkinManagerDialog(self, self.settings, nick, self.instances)
        dlg.exec()

    def play(self, _checked=False, inst=None):
        inst = inst or self.current
        if not inst:
            return
        retry, self._ai_retry = self._ai_retry, False
        if not retry:
            self._ai_attempts.pop(str(inst.folder), None)
            self._ai_history.pop(str(inst.folder), None)
        nick = self.current_nick.strip() or "Player"
        self._add_nick(nick)
        if self.tools_window:
            self.tools_window.close()
        self.tools_window = ToolsDialog(self, inst, self, start_tab="logs")

        self.play_btn.setEnabled(False)
        self.play_btn.setText("Загрузка...")
        self.hero_play.setEnabled(False)
        self.hero_play.setText("Загрузка...")
        self.status.setText("Загрузка...")
        if not retry:
            self._start_launch_anim()
        self.bar.setMaximum(1)
        self.bar.setValue(0)

        w = LaunchWorker(inst.loader, inst.mc_version, nick,
                         inst.ram_mb, inst.loader_version,
                         inst_dir=inst.folder)
        w.status.connect(lambda t: (self.status.setText(t),
                                    self.tools_window.append_log(f"[status] {t}")))
        w.progress.connect(self._progress)
        w.log.connect(self.tools_window.append_log)
        w.failed.connect(self._error)
        w.finished.connect(self._finished)
        w.game_started.connect(lambda ts, i=inst: self._on_game_started(i, ts))
        w.finished_game.connect(lambda sec, i=inst: self._game_finished(sec, i))
        w.crashed.connect(self._on_crash)
        w.perf_ready.connect(lambda s, i=inst: self._on_perf_ready(i, s))
        self.launch_worker = w
        spawn(w)

    def _start_launch_anim(self):
        if not self.settings.value("launch_anim", True, type=bool):
            return
        old = getattr(self, "_splash", None)
        if old is not None and not sip.isdeleted(old):
            old.hide()
            old.deleteLater()
        self._splash = LaunchSplash(self, self.settings.value("theme", "dark"))
        self._splash.show()
        self._splash.raise_()

    def _progress(self, v, m):
        self.bar.setMaximum(m or 1)
        self.bar.setValue(v)
        if m > 0:
            self.progress_label.setText(f"{int(v * 100 / m)}%")

    def _game_finished(self, sec, inst=None):
        inst = inst or self.current
        if inst and sec > 0:
            inst.add_session(sec)
        if inst and sec >= 120:
            self._ai_attempts.pop(str(inst.folder), None)
            self._ai_history.pop(str(inst.folder), None)

    def _on_perf_ready(self, inst, sess):
        sessions = load_perf_sessions(inst.folder)
        delta, verdict = perf_verdict(sessions)
        line = f"[качество] оценка запуска: {sess['score']}/100 ({perf_grade(sess['score'])})"
        if verdict:
            line += " - " + verdict
        tw = self.tools_window
        if tw is not None:
            try:
                if tw.inst.folder == inst.folder:
                    tw.append_log(line)
                    tw.perf_tab.reload()
            except RuntimeError:
                pass
        if verdict and delta <= -15 and not sess.get("crashed"):
            QMessageBox.information(self, "Игра стала работать хуже",
                                    verdict[0].upper() + verdict[1:] + ".\n\n"
                                    "Подробности: меню сборки → «Шкала игры».")

    def _on_crash(self, summary, body, crash_dir):
        inst = self._playing_inst or self.current

        def classic():
            self._crash_dlg = CrashDialog(self, summary, body, crash_dir)
            self._crash_dlg.show()

        if inst is None or not self._ai_on():
            classic()
            return
        if not self._ai_run(self._instance_target(inst), str(inst.folder), summary, body,
                            lambda: self._ai_relaunch(inst), classic):
            summary += "\nИИ-починка исчерпала попытки, дальше нужен ты."
            classic()

    def _ai_key(self) -> str:
        return (self.settings.value("ai_api_key", "", type=str)
                or os.environ.get("ANTHROPIC_API_KEY", "")).strip()

    def _ai_on(self) -> bool:
        if ai_fixer is None:
            return False
        return bool(self._ai_key()) and self.settings.value("ai_enabled", True, type=bool)

    def _instance_target(self, inst):
        def set_ram(v):
            inst.ram_mb = v
            inst.save()
        return ai_fixer.Target("instance", inst.folder, inst.name, inst.mc_version, inst.loader,
                               inst.ram_mb, set_ram=set_ram, loader_version=inst.loader_version,
                               started_at=self._game_started_ts)

    def _server_target(self, srv):
        def set_ram(v):
            srv.ram_mb = v
            srv.save()

        def set_port(v):
            srv.port = v
            srv.save()
        return ai_fixer.Target("server", srv.folder, srv.name, srv.mc_version, srv.core, srv.ram_mb,
                               set_ram=set_ram, set_port=set_port, port=srv.port,
                               started_at=self._server_started_at)

    def _ai_relaunch(self, inst):
        if self.launch_worker is not None and self.launch_worker.isRunning():
            return
        self._ai_retry = True
        self.play(inst=inst)

    def _ai_run(self, target, key, summary, body, restart_cb, fallback_cb) -> bool:
        if ai_fixer is None:
            return False
        if key in self._ai_busy:
            return True
        max_att = self.settings.value("ai_max_attempts", 3, type=int)
        used = self._ai_attempts.get(key, 0)
        if used >= max_att:
            self._ai_attempts.pop(key, None)
            self._ai_history.pop(key, None)
            return False
        self._ai_attempts[key] = used + 1
        self._ai_busy.add(key)
        history = self._ai_history.setdefault(key, [])
        auto_apply = self.settings.value("ai_auto_apply", True, type=bool)
        auto_restart = self.settings.value("ai_auto_restart", True, type=bool)
        model = self.settings.value("ai_model", "", type=str).strip() or ai_fixer.DEFAULT_MODEL
        theme = self.settings.value("theme", "dark")

        dlg = ai_fixer.AiFixDialog(self, f"ИИ-починка: {target.name}", theme, used + 1, max_att)
        dlg.logs_btn.clicked.connect(fallback_cb)
        self._ai_dialog = dlg
        dlg.show()
        state = {"results": [], "cancel_restart": False}

        def start(plan=None):
            w = ai_fixer.AiFixWorker(target, self._ai_key(), model, summary, body, history,
                                     auto_apply, plan)
            w.status.connect(dlg.set_status)
            w.plan_ready.connect(on_plan)
            w.done.connect(on_done)
            w.failed.connect(on_failed)
            spawn(w)

        def on_plan(plan):
            will_apply = bool(auto_apply and plan["actions"] and plan["confidence"] != "low")
            dlg.show_plan(plan, will_apply)
            if plan["actions"] and not will_apply:
                try:
                    dlg.apply_btn.clicked.disconnect()
                except TypeError:
                    pass
                dlg.apply_btn.clicked.connect(lambda: (dlg.apply_btn.setVisible(False),
                                                       self._ai_busy.add(key), start(plan)))

        def on_done(res):
            plan, results = res["plan"], res["results"]
            self._ai_busy.discard(key)
            dlg.show_results(results)
            state["results"] = results
            if results:
                history.append({
                    "diagnosis": plan["diagnosis"],
                    "actions": [{"type": r["type"], "args": r["args"], "ok": r["ok"], "detail": r["detail"]}
                                for r in results],
                    "outcome": "after these fixes the next run crashed again",
                })
            if not results and plan["actions"]:
                return
            ok_n = sum(1 for r in results if r["ok"])
            if ok_n and not plan["needs_human"] and auto_restart:
                dlg.set_status("Готово. Перезапускаю через 3 секунды...")
                QTimer.singleShot(3000, lambda: None if state["cancel_restart"] else restart_cb())
            elif ok_n:
                dlg.set_status("Исправлено частично, дальше нужен ты.")
            elif plan["actions"]:
                dlg.setStatus("Ничего не удалось применить.")
            else:
                dlg.set_status("Автоматически не чинится.")

        def on_failed(msg):
            self._ai_busy.discard(key)
            dlg.show_error(msg)

        def undo():
            state["cancel_restart"] = True
            for m in ai_fixer.undo_results(target, state["results"]):
                dlg.add("  ↺ " + m)
            dlg.undo_btn.setVisible(False)
            dlg.set_status("Откатил. Автоперезапуск отменён.")

        dlg.undo_btn.clicked.connect(undo)
        start()
        return True

    def force_close_game(self):
        w = self.launch_worker
        if w is None or not w.isRunning() or not w.kill_game():
            self.kill_btn.setVisible(False)
            return
        self.kill_btn.setEnabled(False)
        self.status.setText("Закрываю Minecraft...")

    def _finished(self):
        self._playing_inst = None
        self.kill_btn.setVisible(False)
        self._update_presence()
        self.play_btn.setEnabled(self.current is not None)
        self.play_btn.setText("Играть")
        self.hero_play.setEnabled(self.current is not None)
        self.hero_play.setText("Играть")
        self.bar.setMaximum(1)
        self.bar.setValue(0)
        self.progress_label.setText("")
        if self.current:
            self._on_select(self.inst_list.currentRow())

    def _error(self, msg):
        self.status.setText("Ошибка")
        self.play_btn.setText("Играть")
        self.play_btn.setEnabled(self.current is not None)
        self.hero_play.setText("Играть")
        self.hero_play.setEnabled(self.current is not None)
        if self.tools_window:
            self.tools_window.append_log(f"ERROR: {msg}")
        QMessageBox.critical(self, "Ошибка", msg)

    def refresh_servers(self):
        self.servers = load_all_servers()
        cur_name = self.current_server.name if self.current_server else None
        self.srv_list.blockSignals(True)
        self.srv_list.clear()
        self.current_server = None
        for i, srv in enumerate(self.servers):
            item = QListWidgetItem()
            item.setSizeHint(QSize(300, 64))
            self.srv_list.addItem(item)
            card = ServerCard(srv, False)
            card.clicked.connect(lambda s: self._select_server_card(s))
            card.gear_clicked.connect(self._server_menu_gear)
            self.srv_list.setItemWidget(item, card)
            if srv.name == cur_name:
                self.srv_list.setCurrentRow(i)
        self.srv_list.blockSignals(False)
        if self.servers and self.srv_list.currentRow() < 0:
            self.srv_list.setCurrentRow(0)
            self._on_server_select(0)
        elif not self.servers:
            self._on_server_select(-1)

    def _select_server_card(self, srv):
        for i, x in enumerate(self.servers):
            if x.folder == srv.folder:
                self.srv_list.setCurrentRow(i)
                self._on_server_select(i)
                return

    def _on_server_select(self, row: int):
        if not self.server_tab_active:
            return
        for i in range(self.srv_list.count()):
            item = self.srv_list.item(i)
            card = self.srv_list.itemWidget(item)
            if card:
                card.setProperty("selected", "true" if i == row else "false")
                card.style().unpolish(card)
                card.style().polish(card)

        if row < 0 or row >= len(self.servers):
            self.current_server = None
            self.inst_title.setText("Сервер не выбран")
            self.inst_sub.setText("Создай сервер кнопкой «Создать сервер»")
            self.start_srv_btn.setEnabled(False)
            self.stop_srv_btn.setEnabled(False)
            self.console_srv_btn.setEnabled(False)
            self.copy_addr_btn.setEnabled(False)
            return

        self.current_server = self.servers[row]
        srv = self.current_server
        self.inst_title.setText(srv.name)
        running = self.server_runner is not None and self.server_runner.isRunning()
        addr = srv.playit_address or "— адрес playit не задан —"
        self.inst_sub.setText(
            f"{srv.core_label} {srv.mc_version} — RAM {srv.ram_mb // 1024} GB — "
            f"порт {srv.port} — {'работает' if running else 'остановлен'}\n"
            f"Адрес для друзей: {addr}"
        )
        self.start_srv_btn.setEnabled(not running)
        self.stop_srv_btn.setEnabled(running)
        self.console_srv_btn.setEnabled(True)
        self.copy_addr_btn.setEnabled(bool(srv.playit_address))

    def create_server(self):
        dlg = NewServerDialog(self)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        v = dlg.values()
        if not v["mc_version"]:
            QMessageBox.warning(self, "Нет версии", "Список версий ещё не загружен.")
            return
        folder = unique_server_folder(v["name"])
        srv = ServerInstance(folder, v["name"], v["mc_version"], v["core"],
                             v["ram_mb"], v["port"], v["playit_address"])
        srv.save()
        folder.mkdir(parents=True, exist_ok=True)

        core, mc = v["core"], v["mc_version"]
        if core == "paper":
            title, sub = f"Скачиваю Paper {mc}...", "Подожди, это может занять минуту"
            worker = PaperInstaller(mc, srv.jar_path)
        elif core == "fabric":
            title, sub = f"Ставлю Fabric {mc}...", "Подожди..."
            worker = FabricServerInstaller(mc, srv.jar_path)
        elif core in ("forge", "neoforge"):
            title = f"Ставлю {srv.core_label} {mc}..."
            sub = "Установка занимает несколько минут, не закрывай лаунчер"
            worker = ForgeLikeServerInstaller(core, mc, folder)
        else:
            title, sub = f"Скачиваю server.jar {mc}...", "Подожди..."
            worker = VanillaServerInstaller(mc, srv.jar_path)
        self.inst_title.setText(title)
        self.inst_sub.setText(sub)
        self.bar.setMaximum(0)
        worker.status.connect(lambda s: self.inst_sub.setText(s))
        worker.progress.connect(self._progress)
        worker.done.connect(lambda ver: self._on_server_install_done(srv, ver))
        worker.failed.connect(lambda m: self._on_server_install_failed(srv, m))
        self.server_install_worker = worker
        spawn(worker)

    def _on_server_install_done(self, srv, ver: str):
        self.bar.setMaximum(1)
        self.bar.setValue(0)
        modded = srv.core in MODDED_CORES
        if modded:
            srv.loader_version = ver
            srv.save()
        ensure_eula_and_properties(srv, log=lambda s: None)
        self.refresh_servers()
        self._switch_tab(True)
        core_line = (f"Ядро: {srv.core_label} {ver} (Minecraft {srv.mc_version})" if modded
                     else f"Ядро: {srv.core_label} {ver}")
        tip = ("\n\nМоды ставятся через меню сервера («Моды (Modrinth)») "
               "или копированием .jar в папку mods." if modded else "")
        QMessageBox.information(
            self, "Сервер создан",
            f"Сервер «{srv.name}» готов.\n\n"
            f"{core_line}\n"
            f"Порт: {srv.port}\n"
            f"Адрес для друзей: {srv.playit_address or '(не задан)'}{tip}\n\n"
            f"При первом запуске playit.gg сам скачается и в консоли сервера "
            f"появится ссылка вида playit.gg/claim/... - её нужно один раз открыть "
            f"в браузере и подтвердить, дальше всё будет запускаться само."
        )

    def _on_server_install_failed(self, srv, msg: str):
        self.bar.setMaximum(1)
        self.bar.setValue(0)
        if srv.core in MODDED_CORES:
            shutil.rmtree(srv.folder, ignore_errors=True)
        QMessageBox.critical(self, "Не удалось установить ядро", msg)

    def delete_current_server(self):
        if not self.current_server:
            QMessageBox.information(self, "Нет сервера", "Сначала выбери сервер.")
            return
        srv = self.current_server
        running = self.server_runner is not None and self.server_runner.isRunning()
        if running:
            QMessageBox.warning(self, "Сервер запущен", "Сначала останови сервер.")
            return
        if QMessageBox.question(
            self, "Удалить сервер",
            f"Удалить сервер «{srv.name}» со всеми мирами и плагинами?\nЭто нельзя отменить."
        ) != QMessageBox.StandardButton.Yes:
            return
        shutil.rmtree(srv.folder, ignore_errors=True)
        self.current_server = None
        self.refresh_servers()

    def start_server(self):
        if not self.current_server:
            return
        if self.server_runner is not None and self.server_runner.isRunning():
            QMessageBox.information(self, "Уже запущен", "Сервер уже работает.")
            return
        srv = self.current_server
        self._running_server = srv
        self._server_log.clear()
        self._server_stop_requested = False
        self._server_started_at = time.time()
        if srv.launch_args() is None:
            QMessageBox.warning(
                self, "Нет ядра",
                f"Ядро сервера ({srv.core_label}) не найдено.\n"
                f"Удали сервер и создай заново — либо положи ядро вручную в:\n{srv.folder}"
            )
            return
        ensure_eula_and_properties(srv, log=lambda s: None)

        try:
            java = ensure_java(srv.mc_version,
                               lambda s: self.inst_sub.setText(s),
                               lambda v, m: self._progress(v, m))
        except Exception as e:
            QMessageBox.critical(self, "Java не найдена", str(e))
            return

        playit = None
        if srv.auto_playit:
            try:
                playit = ensure_playit(log=lambda s: self.inst_sub.setText(s))
            except Exception as e:
                QMessageBox.warning(
                    self, "playit.gg",
                    f"Не удалось подготовить playit.gg автоматически:\n{e}\n\n"
                    f"Сервер запустится без доступа для друзей."
                )
                playit = None

        self.server_runner = ServerRunner(srv, java, playit)
        self.server_runner.status.connect(self.inst_sub.setText)
        self.server_runner.started.connect(self._on_server_started)
        self.server_runner.stopped.connect(self._on_server_stopped)
        self.server_runner.failed.connect(self._on_server_failed)
        self.server_runner.log.connect(self._on_server_log)

        if self.server_console is not None:
            self.server_console.runner = self.server_runner
            self.server_runner.log.connect(self.server_console.append)

        self.start_srv_btn.setEnabled(False)
        self.stop_srv_btn.setEnabled(True)
        spawn(self.server_runner)

    def _on_server_started(self):
        if self.current_server:
            self.inst_sub.setText(self.inst_sub.text().replace("остановлен", "работает"))
        self.start_srv_btn.setEnabled(False)
        self.stop_srv_btn.setEnabled(True)

    def _on_server_stopped(self, code: int):
        self.start_srv_btn.setEnabled(True)
        self.stop_srv_btn.setEnabled(False)
        if self.current_server:
            s = self.current_server
            addr = s.playit_address or "— адрес playit не задан —"
            self.inst_sub.setText(
                f"{s.core_label} {s.mc_version} — RAM {s.ram_mb // 1024} GB — "
                f"порт {s.port} — остановлен\nАдрес для друзей: {addr}"
            )
        self.server_runner = None
        srv = self._running_server
        if srv is not None and code != 0 and not self._server_stop_requested and self._ai_on():
            if time.time() - self._server_started_at > 300:
                self._ai_attempts.pop(str(srv.folder), None)
                self._ai_history.pop(str(srv.folder), None)
            self._ai_on_server_crash(srv, code)

    def _on_server_failed(self, msg: str):
        self.start_srv_btn.setEnabled(True)
        self.stop_srv_btn.setEnabled(False)
        self.server_runner = None
        srv = self._running_server
        if srv is not None and not self._server_stop_requested and self._ai_on():
            self._server_log.append(msg)
            self._ai_on_server_crash(srv, -1, msg)
            return
        QMessageBox.critical(self, "Сервер не запустился", msg)

    def _ai_on_server_crash(self, srv, code: int, note: str = ""):
        summary = f"Сервер {srv.core} {srv.mc_version} завершился с кодом {code}. {note}".strip()
        body = "\n".join(self._server_log)
        if ai_fixer is None:
            QMessageBox.warning(self, "Сервер упал", summary)
            return

        def restart():
            if self.server_runner is not None and self.server_runner.isRunning():
                return
            self.current_server = srv
            self.start_server()

        if not self._ai_run(self._server_target(srv), str(srv.folder), summary, body, restart, lambda: None):
            QMessageBox.warning(self, "Сервер упал",
                                summary + "\n\nИИ-починка исчерпала попытки.")

    def _on_server_log(self, line: str):
        self._server_log.append(line)
        if self.server_console is not None:
            self.server_console.append(line)

    def stop_server(self):
        if self.server_runner is None:
            return
        if QMessageBox.question(self, "Остановить сервер",
                                "Отправить серверу команду stop?\nМир будет сохранён.") \
                != QMessageBox.StandardButton.Yes:
            return
        self._server_stop_requested = True
        self.inst_sub.setText("Останавливаю сервер (stop)...")
        self.server_runner.stop()

    def open_server_console(self):
        if not self.current_server:
            return
        if self.server_console is None:
            theme = self.settings.value("theme", "dark")
            self.server_console = ServerConsoleDialog(self, self.current_server,
                                                      self.server_runner, theme)
        else:
            self.server_console.srv = self.current_server
            self.server_console.runner = self.server_runner
        self.server_console.show()
        self.server_console.raise_()
        self.server_console.activateWindow()

    def copy_server_address(self):
        if not self.current_server:
            return
        addr = self.current_server.playit_address
        if not addr:
            QMessageBox.information(self, "Нет адреса",
                                    "У сервера не задан адрес playit.\n"
                                    "Открой server.json в папке сервера и впиши его.")
            return
        QApplication.clipboard().setText(addr)
        QMessageBox.information(self, "Скопировано", f"Адрес скопирован:\n{addr}")

    def _server_menu(self, pos: QPoint):
        item = self.srv_list.itemAt(pos)
        if not item:
            return
        row = self.srv_list.row(item)
        self.srv_list.setCurrentRow(row)
        self._on_server_select(row)
        self._show_server_menu(self.srv_list.mapToGlobal(pos))

    def _server_menu_gear(self, srv):
        self._select_server_card(srv)
        self._show_server_menu(self.cursor().pos())

    def _show_server_menu(self, global_pos: QPoint):
        if not self.current_server:
            return
        srv = self.current_server
        running = self.server_runner is not None and self.server_runner.isRunning()
        menu = QMenu(self)
        if running:
            menu.addAction("Остановить сервер").triggered.connect(self.stop_server)
        else:
            menu.addAction("Запустить сервер").triggered.connect(self.start_server)
        menu.addAction("Открыть консоль").triggered.connect(self.open_server_console)
        if srv.core == "paper":
            menu.addAction("Плагины (Modrinth)").triggered.connect(self.open_server_plugins)
        elif srv.core in MODDED_CORES:
            menu.addAction("Моды (Modrinth)").triggered.connect(self.open_server_mods)
        menu.addAction("Копировать адрес для друзей").triggered.connect(self.copy_server_address)
        menu.addSeparator()
        menu.addAction("Открыть папку сервера").triggered.connect(
            lambda: QDesktopServices.openUrl(QUrl.fromLocalFile(str(srv.folder))))
        menu.addSeparator()
        menu.addAction("Удалить сервер").triggered.connect(self.delete_current_server)
        menu.exec(global_pos)

    def open_server_plugins(self):
        if not self.current_server:
            return
        dlg = ServerPluginsDialog(self, self.current_server, self)
        dlg.exec()

    def open_server_mods(self):
        if not self.current_server:
            return
        dlg = ServerModsDialog(self, self.current_server, self)
        dlg.exec()


if __name__ == "__main__":
    if os.name == "nt":
        try:
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("Frimer.Launcher.2")
        except Exception:
            pass
    app = QApplication(sys.argv)
    win = Launcher()
    win.show()
    sys.exit(app.exec())