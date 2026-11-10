
import cv2
import numpy as np
import onnxruntime as ort
from collections import Counter
from pathlib import Path

# --------------------------------------------------
# Configuration
# --------------------------------------------------
MODEL = "best.onnx"

IMAGE_FILES = [
    "nice.jpg",
    "light_dull.jpg",
    "angle.jpg",
]

SIZE = 640
CONF = 0.25
NMS = 0.45

CLASS_NAMES = [
    "broken_tablet",
    "empty_pocket",
    "filled_tablet",
]

# --------------------------------------------------
# Load model once for all three images
# --------------------------------------------------
print("Loading YOLO ONNX model...")

session = ort.InferenceSession(
    MODEL,
    providers=["CPUExecutionProvider"]
)

input_name = session.get_inputs()[0].name

print("Model loaded successfully.")
print("Input:", input_name)
print()

# --------------------------------------------------
# Process one image
# --------------------------------------------------
def process_image(image_path):
    print("=" * 55)
    print(f"Image: {image_path}")

    im = cv2.imread(image_path)

    if im is None:
        print(f"ERROR: Could not read '{image_path}'")
        print("Check the filename and project folder.")
        return

    original = im.copy()
    h, w = im.shape[:2]

    # Letterbox resize while preserving aspect ratio
    scale = min(SIZE / w, SIZE / h)
    nw = round(w * scale)
    nh = round(h * scale)

    resized = cv2.resize(im, (nw, nh))

    canvas = np.full(
        (SIZE, SIZE, 3),
        114,
        dtype=np.uint8
    )

    left = (SIZE - nw) // 2
    top = (SIZE - nh) // 2

    canvas[top:top + nh, left:left + nw] = resized

    # BGR -> RGB, HWC -> CHW, normalize to [0, 1]
    tensor = canvas[:, :, ::-1].transpose(2, 0, 1)
    tensor = np.ascontiguousarray(
        tensor[None],
        dtype=np.float32
    ) / 255.0

    # Run model inference
    output = session.run(
        None,
        {input_name: tensor}
    )[0]

    # Expected output: (1, 7, 8400)
    pred = output[0].T

    boxes = []
    scores = []
    classes = []

    # Decode YOLO predictions
    for row in pred:
        class_id = int(np.argmax(row[4:7]))
        score = float(row[4 + class_id])

        if score < CONF:
            continue

        cx, cy, bw, bh = map(float, row[:4])

        # Convert from model coordinates to original image
        x1 = (cx - bw / 2 - left) / scale
        y1 = (cy - bh / 2 - top) / scale
        x2 = (cx + bw / 2 - left) / scale
        y2 = (cy + bh / 2 - top) / scale

        # Clip to image boundaries
        x1 = max(0, min(w - 1, x1))
        y1 = max(0, min(h - 1, y1))
        x2 = max(0, min(w - 1, x2))
        y2 = max(0, min(h - 1, y2))

        if x2 <= x1 or y2 <= y1:
            continue

        boxes.append([
            int(x1),
            int(y1),
            int(x2 - x1),
            int(y2 - y1)
        ])
        scores.append(score)
        classes.append(class_id)

    # Apply NMS separately for each class
    keep = []

    for class_id in range(len(CLASS_NAMES)):
        ids = [
            i for i, c in enumerate(classes)
            if c == class_id
        ]

        if not ids:
            continue

        local_indices = cv2.dnn.NMSBoxes(
            [boxes[i] for i in ids],
            [scores[i] for i in ids],
            CONF,
            NMS
        )

        if local_indices is not None and len(local_indices) > 0:
            keep.extend(
                ids[int(j)]
                for j in np.array(local_indices).reshape(-1)
            )

    # Count final detections
    counts = Counter(classes[i] for i in keep)

    print(f"Detections after NMS: {len(keep)}")

    for class_id, name in enumerate(CLASS_NAMES):
        print(f"{name}: {counts[class_id]}")

    # Draw boxes on original image
    for i in keep:
        x, y, bw, bh = boxes[i]
        label = f"{CLASS_NAMES[classes[i]]} {scores[i]:.2f}"

        cv2.rectangle(
            original,
            (x, y),
            (x + bw, y + bh),
            (0, 255, 0),
            2
        )

        cv2.putText(
            original,
            label,
            (x, max(20, y - 6)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 255, 0),
            2
        )

    # Save a separate result for each image
    result_path = Path(image_path).with_name(
        Path(image_path).stem + "_result.jpg"
    )

    if cv2.imwrite(str(result_path), original):
        print(f"Annotated image saved: {result_path}")
    else:
        print(f"ERROR: Could not save {result_path}")

# --------------------------------------------------
# Test all three images
# --------------------------------------------------
for image_file in IMAGE_FILES:
    process_image(image_file)

print()
print("=" * 55)
print("All image tests finished.")