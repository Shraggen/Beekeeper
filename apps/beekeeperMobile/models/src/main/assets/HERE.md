### 🐝 Vosk Speech Model Location 🗣️

**Action Required:** Download and place the Vosk speech model here.

**Steps:**

1.  **Download:** Get your desired language model from the official [Vosk Models page](https://alphacephei.com/vosk/models).
2.  **Unzip:** Extract the downloaded file.
3.  **Copy:** Place the **entire contents** of the unzipped model folder directly into this directory.

**Example:** After unzipping `vosk-model-en-us-0.22`, copy all its files and folders (e.g., `am`, `conf`, `graph`, etc.) into this `assets/` directory.

**Final Path:** The structure should look like `models/src/main/assets/vosk-model-en-us-0.22/...` (or similar, depending on the model). The app's code will automatically look for the model in this location.

---

_This file is versioned to guide others. **Do not** commit the actual Vosk model files, as they are too large._