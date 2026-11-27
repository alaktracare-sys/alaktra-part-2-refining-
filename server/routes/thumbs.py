from fastapi import APIRouter, Request, HTTPException
from fastapi.responses import FileResponse, Response
import hashlib
import mimetypes
import os
import uuid

from database import COVER_DIR, COVER_THUMB_DIR

router = APIRouter(tags=["thumbs"])

ALLOWED_THUMB_SIZES = (128, 256, 400, 800)


def _etag_matches(client_etag: str | None, server_etag: str) -> bool:
    if not client_etag:
        return False
    target = server_etag.strip()
    if target.startswith(("W/", "w/")):
        target = target[2:].strip()
    target = target.strip('"')
    for part in client_etag.split(","):
        cand = part.strip()
        if cand == "*":
            return True
        if cand.startswith(("W/", "w/")):
            cand = cand[2:].strip()
        cand = cand.strip('"')
        if cand and cand == target:
            return True
    return False


def _sanitize_cover_filename(filename: str) -> str:
    if not filename or "/" in filename or "\\" in filename or ".." in filename:
        raise HTTPException(status_code=400, detail="Invalid filename")
    safe_name = os.path.basename(filename)
    if not safe_name or safe_name in (".", ".."):
        raise HTTPException(status_code=400, detail="Invalid filename")
    return safe_name


def _resolve_cover_source(filename: str) -> tuple[str, str]:
    safe_name = _sanitize_cover_filename(filename)
    cover_root = os.path.realpath(COVER_DIR)
    source_path = os.path.realpath(os.path.join(cover_root, safe_name))
    if not source_path.startswith(cover_root + os.sep):
        raise HTTPException(status_code=400, detail="Invalid cover path")
    if not os.path.isfile(source_path):
        raise HTTPException(status_code=404, detail="Cover image not found")
    return safe_name, source_path


def invalidate_cover_thumbs(filename: str) -> None:
    if not filename:
        return
    safe_name = os.path.basename(filename)
    if not safe_name or safe_name in (".", "..") or "/" in safe_name or "\\" in safe_name:
        return
    for size in ALLOWED_THUMB_SIZES:
        thumb_path = os.path.join(COVER_THUMB_DIR, str(size), safe_name)
        try:
            if os.path.exists(thumb_path):
                os.remove(thumb_path)
        except OSError:
            pass


def _generate_thumbnail(source_path: str, thumb_path: str, size: int) -> bool:
    os.makedirs(os.path.dirname(thumb_path), exist_ok=True)
    tmp_path = f"{thumb_path}.{uuid.uuid4().hex}.tmp"
    try:
        from PIL import Image, ImageOps

        resampling = getattr(Image, "Resampling", Image).LANCZOS
        with Image.open(source_path) as raw_img:
            img = ImageOps.exif_transpose(raw_img)
            img.thumbnail((size, size), resampling)
            ext = os.path.splitext(thumb_path)[1].lower()
            if ext == ".png":
                img.save(tmp_path, format="PNG", optimize=True)
            elif ext == ".webp":
                img.save(tmp_path, format="WEBP", quality=84, method=4)
            else:
                if img.mode in ("RGBA", "LA", "P"):
                    bg = Image.new("RGB", img.size, (18, 18, 18))
                    rgba = img.convert("RGBA")
                    bg.paste(rgba, mask=rgba.split()[-1])
                    img = bg
                elif img.mode != "RGB":
                    img = img.convert("RGB")
                img.save(tmp_path, format="JPEG", quality=84, optimize=True)
        os.replace(tmp_path, thumb_path)
        return True
    except Exception:
        try:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
        except OSError:
            pass
        return False


@router.get("/cover-thumb/{size}/{filename}")
def get_cover_thumbnail(size: int, filename: str, request: Request):
    if size not in ALLOWED_THUMB_SIZES:
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported thumbnail size. Allowed: {', '.join(str(s) for s in ALLOWED_THUMB_SIZES)}",
        )

    safe_name, source_path = _resolve_cover_source(filename)
    st = os.stat(source_path)
    sig = f"{size}:{safe_name}:{st.st_mtime_ns}:{st.st_size}"
    etag = f'"{hashlib.md5(sig.encode()).hexdigest()}"'
    cache_headers = {
        "ETag": etag,
        "Cache-Control": "public, max-age=86400, must-revalidate",
    }

    if _etag_matches(request.headers.get("if-none-match"), etag):
        return Response(status_code=304, headers=cache_headers)

    thumb_path = os.path.join(COVER_THUMB_DIR, str(size), safe_name)
    serve_path = source_path

    if os.path.isfile(thumb_path):
        try:
            if os.stat(thumb_path).st_mtime_ns >= st.st_mtime_ns:
                serve_path = thumb_path
            elif _generate_thumbnail(source_path, thumb_path, size):
                serve_path = thumb_path
        except OSError:
            if _generate_thumbnail(source_path, thumb_path, size):
                serve_path = thumb_path
    else:
        if _generate_thumbnail(source_path, thumb_path, size):
            serve_path = thumb_path

    media_type, _ = mimetypes.guess_type(serve_path)
    return FileResponse(
        serve_path,
        media_type=media_type or "image/jpeg",
        headers=cache_headers,
    )
