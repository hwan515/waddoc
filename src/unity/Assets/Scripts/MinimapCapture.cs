using System.IO;
using UnityEngine;

public class MinimapCapture : MonoBehaviour
{
    [Header("References")]
    public Camera captureCamera;
    public RenderTexture targetTexture;

    [Header("Capture Options")]
    public bool captureOnStart = true;
    public KeyCode captureKey = KeyCode.P;

    [Header("Output")]
    public string fileName = "minimap.png";
    public bool addTimestampToFileName = false;

    [Header("PNG Settings")]
    public bool useTransparentBackground = false;

    private string outputDirectory;

    void Start()
    {
        outputDirectory = GetOutputDirectory();

        if (!Directory.Exists(outputDirectory))
        {
            Directory.CreateDirectory(outputDirectory);
        }

        if (captureOnStart)
        {
            CaptureAndSave();
        }
    }

    void Update()
    {
        if (Input.GetKeyDown(captureKey))
        {
            CaptureAndSave();
        }
    }

    [ContextMenu("Capture And Save")]
    public void CaptureAndSave()
    {
        if (captureCamera == null)
        {
            Debug.LogError("[MinimapCapture] captureCamera가 비어 있음");
            return;
        }

        if (targetTexture == null)
        {
            Debug.LogError("[MinimapCapture] targetTexture(RenderTexture)가 비어 있음");
            return;
        }

        RenderTexture prevActive = RenderTexture.active;
        RenderTexture prevCameraTarget = captureCamera.targetTexture;

        try
        {
            captureCamera.targetTexture = targetTexture;
            RenderTexture.active = targetTexture;

            // 카메라 렌더
            captureCamera.Render();

            TextureFormat texFormat = useTransparentBackground
                ? TextureFormat.RGBA32
                : TextureFormat.RGB24;

            Texture2D tex = new Texture2D(
                targetTexture.width,
                targetTexture.height,
                texFormat,
                false
            );

            tex.ReadPixels(
                new Rect(0, 0, targetTexture.width, targetTexture.height),
                0,
                0
            );
            tex.Apply();

            byte[] pngBytes = tex.EncodeToPNG();

            string finalFileName = fileName;
            if (addTimestampToFileName)
            {
                string name = Path.GetFileNameWithoutExtension(fileName);
                string ext = Path.GetExtension(fileName);
                string timeStamp = System.DateTime.Now.ToString("yyyyMMdd_HHmmss");
                finalFileName = $"{name}_{timeStamp}{ext}";
            }

            string fullPath = Path.Combine(outputDirectory, finalFileName);
            File.WriteAllBytes(fullPath, pngBytes);

            Debug.Log($"[MinimapCapture] 저장 완료: {fullPath}");

            Destroy(tex);
        }
        catch (System.Exception e)
        {
            Debug.LogError($"[MinimapCapture] 저장 실패: {e.Message}");
        }
        finally
        {
            captureCamera.targetTexture = prevCameraTarget;
            RenderTexture.active = prevActive;
        }
    }

    private string GetOutputDirectory()
    {
#if UNITY_EDITOR
        return Path.Combine(Application.dataPath, "../CapturedMinimap");
#else
        return Path.Combine(Application.persistentDataPath, "CapturedMinimap");
#endif
    }
}