from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect

from app.services.stt_stream_service import SttStreamService, get_stt_stream_service

router = APIRouter(tags=["stt"])


@router.websocket("/api/v1/stt/streams/{session_id}/{turn_id}")
async def stt_stream(
    websocket: WebSocket,
    session_id: str,
    turn_id: str,
    service: SttStreamService = Depends(get_stt_stream_service),
) -> None:
    await websocket.accept()

    try:
        await service.handle_connection(websocket, session_id, turn_id)
        await service.close(websocket)
    except WebSocketDisconnect:
        await service.close(websocket)
