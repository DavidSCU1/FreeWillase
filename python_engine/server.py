from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from modules.pipeline import run_pipeline
import os
import uuid

app = FastAPI()

class PredictionRequest(BaseModel):
    sequence: str
    api_key: str
    env_text: str = ""

@app.post("/predict")
async def predict(request: PredictionRequest):
    try:
        task_id = str(uuid.uuid4())
        out_dir = os.path.join("temp", task_id)
        result = run_pipeline(request.sequence, request.api_key, out_dir, request.env_text)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
