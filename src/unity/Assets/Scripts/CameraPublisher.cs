using UnityEngine;
using Unity.Robotics.ROSTCPConnector;
using RosMessageTypes.Sensor;
using RosMessageTypes.Std;
using RosMessageTypes.BuiltinInterfaces;

public class CameraPublisher : MonoBehaviour
{
    [Header("ROS")]
    public string topicName = "/camera/image_raw";
    public string frameId = "front_camera";

    [Header("Camera")]
    public Camera targetCamera;
    public RenderTexture renderTexture;

    [Header("Publish Settings")]
    public int publishHz = 10;
    public bool debugLog = true;

    private ROSConnection ros;
    private Texture2D texture2D;
    private float publishInterval;
    private float lastPublishTime = -999f;

    void Start()
    {
        ros = ROSConnection.GetOrCreateInstance();
        ros.RegisterPublisher<ImageMsg>(topicName);

        if (targetCamera == null)
        {
            Debug.LogError("[CameraPublisher] targetCamera is not assigned.");
            enabled = false;
            return;
        }

        if (renderTexture == null)
        {
            Debug.LogError("[CameraPublisher] renderTexture is not assigned.");
            enabled = false;
            return;
        }

        // 카메라 출력 대상을 RT로 고정
        targetCamera.targetTexture = renderTexture;

        publishInterval = 1.0f / Mathf.Max(1, publishHz);

        texture2D = new Texture2D(
            renderTexture.width,
            renderTexture.height,
            TextureFormat.RGB24,
            false
        );

        if (debugLog)
        {
            Debug.Log(
                $"[CameraPublisher] Started. topic={topicName}, size={renderTexture.width}x{renderTexture.height}"
            );
        }
    }

    void LateUpdate()
    {
        if (Time.time - lastPublishTime < publishInterval)
            return;

        PublishImage();
        lastPublishTime = Time.time;
    }

    void PublishImage()
    {
        if (targetCamera == null || renderTexture == null || texture2D == null)
        {
            Debug.LogWarning("[CameraPublisher] Missing targetCamera/renderTexture/texture2D");
            return;
        }

        RenderTexture previous = RenderTexture.active;

        try
        {
            // 현재 카메라가 RT에 한 프레임을 확실히 렌더하도록 강제
            targetCamera.targetTexture = renderTexture;
            targetCamera.Render();

            // RT 읽기
            RenderTexture.active = renderTexture;

            texture2D.ReadPixels(
                new Rect(0, 0, renderTexture.width, renderTexture.height),
                0,
                0,
                false
            );
            texture2D.Apply(false);

            // Unity 화면 좌표계 상하반전 보정
            Color32[] pixels = texture2D.GetPixels32();
            Color32[] flipped = FlipVertical(pixels, texture2D.width, texture2D.height);
            byte[] imageBytes = ConvertColor32ToRgbBytes(flipped);

            double now = Time.timeAsDouble;
            int sec = (int)now;
            uint nanosec = (uint)((now - sec) * 1e9);

            HeaderMsg header = new HeaderMsg
            {
                frame_id = frameId,
                stamp = new TimeMsg(sec, nanosec)
            };

            ImageMsg imageMsg = new ImageMsg(
                header,
                (uint)renderTexture.height,
                (uint)renderTexture.width,
                "rgb8",
                0,
                (uint)(renderTexture.width * 3),
                imageBytes
            );

            ros.Publish(topicName, imageMsg);

            if (debugLog)
            {
                Debug.Log(
                    $"[CameraPublisher] Published {renderTexture.width}x{renderTexture.height}, " +
                    $"bytes={imageBytes.Length}, topic={topicName}"
                );
            }
        }
        finally
        {
            RenderTexture.active = previous;
        }
    }

    Color32[] FlipVertical(Color32[] src, int width, int height)
    {
        Color32[] dst = new Color32[src.Length];

        for (int y = 0; y < height; y++)
        {
            int flippedY = height - 1 - y;

            for (int x = 0; x < width; x++)
            {
                dst[flippedY * width + x] = src[y * width + x];
            }
        }

        return dst;
    }

    byte[] ConvertColor32ToRgbBytes(Color32[] pixels)
    {
        byte[] bytes = new byte[pixels.Length * 3];

        for (int i = 0; i < pixels.Length; i++)
        {
            bytes[i * 3 + 0] = pixels[i].r;
            bytes[i * 3 + 1] = pixels[i].g;
            bytes[i * 3 + 2] = pixels[i].b;
        }

        return bytes;
    }

    void OnDestroy()
    {
        if (texture2D != null)
        {
            Destroy(texture2D);
        }
    }
}