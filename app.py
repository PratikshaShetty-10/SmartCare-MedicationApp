from flask import Flask, request, jsonify
from ultralytics import YOLO
import cv2
import numpy as np
import os
import re

# ============================================================
# SMARTCARE API
# YOLO = PILL CONDITION + COUNT
# OCR = MEDICINE DATE/TEXT
# ============================================================

app = Flask(__name__)

# ------------------------------------------------------------
# MODEL PATHS
# ------------------------------------------------------------

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

CONDITION_MODEL_PATH = os.path.join(
    BASE_DIR, "models", "pill_condition_best.pt"
)

OCR_MODEL_PATH = os.path.join(
    BASE_DIR, "models", "ocr_best.pt"
)

# ------------------------------------------------------------
# CHECK MODELS
# ------------------------------------------------------------

print("=" * 70)
print("SMARTCARE AI SERVER")
print("=" * 70)

print("Condition model:", CONDITION_MODEL_PATH)
print("OCR model:", OCR_MODEL_PATH)

if not os.path.exists(CONDITION_MODEL_PATH):
    raise FileNotFoundError(
        f"Condition model not found:\n{CONDITION_MODEL_PATH}"
    )

if not os.path.exists(OCR_MODEL_PATH):
    raise FileNotFoundError(
        f"OCR model not found:\n{OCR_MODEL_PATH}"
    )

# ------------------------------------------------------------
# LOAD MODELS ONCE
# ------------------------------------------------------------

print("\nLoading condition model...")
condition_model = YOLO(CONDITION_MODEL_PATH)
print("Condition model loaded ✅")

print("\nLoading OCR model...")
ocr_model = YOLO(OCR_MODEL_PATH)
print("OCR model loaded ✅")

print("\nCondition classes:")
print(condition_model.names)

print("\nOCR classes:")
print(ocr_model.names)

print("=" * 70)


# ============================================================
# YOLO CONDITION DETECTION
# ============================================================

def detect_condition(img):

    results = condition_model.predict(
        source=img,
        conf=0.25,
        imgsz=640,
        verbose=False
    )

    result = results[0]

    ok_count = 0
    broken_count = 0
    empty_count = 0

    detections = []

    if result.boxes is not None:

        for box in result.boxes:

            cls_id = int(box.cls[0])
            confidence = float(box.conf[0])

            class_name = condition_model.names[cls_id]

            if class_name == "OK":
                ok_count += 1

            elif class_name == "BROKEN":
                broken_count += 1

            elif class_name == "Empty":
                empty_count += 1

            detections.append({
                "class": class_name,
                "confidence": round(confidence, 3)
            })

    total = ok_count + broken_count + empty_count

    return {
        "ok_pills": ok_count,
        "broken_pills": broken_count,
        "empty_slots": empty_count,
        "total_objects": total,
        "detections": detections
    }


# ============================================================
# OCR DETECTION
# ============================================================

def detect_ocr(img):

    results = ocr_model.predict(
        source=img,
        conf=0.05,
        imgsz=640,
        verbose=False
    )

    result = results[0]

    characters = []

    if result.boxes is not None:

        for box in result.boxes:

            cls_id = int(box.cls[0])
            confidence = float(box.conf[0])

            class_name = ocr_model.names[cls_id]

            characters.append({
                "character": class_name,
                "confidence": round(confidence, 3)
            })

    # Sort characters approximately from left to right
    if result.boxes is not None and len(result.boxes) > 0:

        items = []

        for box in result.boxes:

            cls_id = int(box.cls[0])
            confidence = float(box.conf[0])

            xyxy = box.xyxy[0].tolist()

            x_position = xyxy[0]

            items.append({
                "character": ocr_model.names[cls_id],
                "confidence": round(confidence, 3),
                "x": x_position
            })

        items.sort(key=lambda x: x["x"])

        text = "".join(item["character"] for item in items)

    else:

        text = ""

    return {
        "text": text,
        "detections": characters
    }


# ============================================================
# DATE EXTRACTION
# ============================================================

def extract_dates(text):

    manufacturing_date = None
    expiry_date = None

    if not text:
        return manufacturing_date, expiry_date

    # Normalize OCR text
    clean_text = text.upper()

    # Convert common OCR separators
    clean_text = clean_text.replace("-", "/")
    clean_text = clean_text.replace(".", "/")

    # Search dates such as:
    # 08/2025
    # 08/25
    # 08/2023

    dates = re.findall(
        r"\b(0?[1-9]|1[0-2])/(20\d{2}|\d{2})\b",
        clean_text
    )

    formatted_dates = []

    for month, year in dates:

        if len(year) == 2:
            year = "20" + year

        formatted_dates.append(
            f"{int(month):02d}/{year}"
        )

    # Remove duplicates
    formatted_dates = list(dict.fromkeys(formatted_dates))

    # Simple assignment:
    # first date = manufacturing
    # second date = expiry

    if len(formatted_dates) >= 1:
        manufacturing_date = formatted_dates[0]

    if len(formatted_dates) >= 2:
        expiry_date = formatted_dates[1]

    return manufacturing_date, expiry_date


# ============================================================
# EXPIRY STATUS
# ============================================================

def get_expiry_status(expiry_date):

    if not expiry_date:
        return "UNKNOWN"

    return "DATE_DETECTED"


# ============================================================
# MAIN API
# ============================================================

@app.route("/analyze", methods=["POST"])
def analyze():

    print("\n" + "=" * 70)
    print("NEW IMAGE RECEIVED")
    print("=" * 70)

    if "image" not in request.files:

        return jsonify({
            "error": "No image uploaded"
        }), 400

    file = request.files["image"]

    # Read image
    file_bytes = file.read()

    npimg = np.frombuffer(
        file_bytes,
        np.uint8
    )

    img = cv2.imdecode(
        npimg,
        cv2.IMREAD_COLOR
    )

    if img is None:

        return jsonify({
            "error": "Invalid image"
        }), 400

    print("Image received ✅")

    # --------------------------------------------------------
    # 1. YOLO CONDITION + COUNT
    # --------------------------------------------------------

    print("\nRunning YOLO condition detection...")

    condition_result = detect_condition(img)

    print(
        "OK:",
        condition_result["ok_pills"]
    )

    print(
        "BROKEN:",
        condition_result["broken_pills"]
    )

    print(
        "EMPTY:",
        condition_result["empty_slots"]
    )

    print(
        "TOTAL:",
        condition_result["total_objects"]
    )

    # --------------------------------------------------------
    # 2. OCR
    # --------------------------------------------------------

    print("\nRunning OCR...")

    ocr_result = detect_ocr(img)

    print(
        "OCR text:",
        ocr_result["text"]
        if ocr_result["text"]
        else "NO TEXT"
    )

    # --------------------------------------------------------
    # 3. DATE EXTRACTION
    # --------------------------------------------------------

    manufacturing_date, expiry_date = extract_dates(
        ocr_result["text"]
    )

    expiry_status = get_expiry_status(
        expiry_date
    )

    # --------------------------------------------------------
    # FINAL RESPONSE
    # --------------------------------------------------------

    response = {

        "actual_count_yolo":
            condition_result["ok_pills"],

        "ok_pills":
            condition_result["ok_pills"],

        "broken_pills":
            condition_result["broken_pills"],

        "empty_slots":
            condition_result["empty_slots"],

        "total_objects":
            condition_result["total_objects"],

        "ocr_text":
            ocr_result["text"],

        "manufacturing_date":
            manufacturing_date,

        "ocr_expiry_date":
            expiry_date,

        "expiry_status":
            expiry_status,

        "condition_detections":
            condition_result["detections"],

        "ocr_detections":
            ocr_result["detections"]
    }

    print("\n" + "=" * 70)
    print("SMARTCARE RESULT")
    print("=" * 70)

    print(response)

    return jsonify(response), 200


# ============================================================
# HEALTH CHECK
# ============================================================

@app.route("/", methods=["GET"])
def home():

    return jsonify({
        "status": "SmartCare API is running",
        "condition_model": "loaded",
        "ocr_model": "loaded"
    })


# ============================================================
# START SERVER
# ============================================================

if __name__ == "__main__":

    print("\n" + "=" * 70)
    print("SMARTCARE SERVER STARTING")
    print("=" * 70)

    print("Server: http://127.0.0.1:5000")
    print("Analyze endpoint: http://127.0.0.1:5000/analyze")

    app.run(
        host="0.0.0.0",
        port=5000,
        debug=False
    )