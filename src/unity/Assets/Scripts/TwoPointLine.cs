using UnityEngine;

public class TwoPointLine : MonoBehaviour
{
    public Transform startPoint;
    public Transform endPoint;
    private LineRenderer lr;

    void Awake()
    {
        lr = gameObject.AddComponent<LineRenderer>();

        lr.startWidth = 0.5f;
        lr.endWidth = 0.5f;
        lr.positionCount = 2;
        lr.useWorldSpace = true;

        lr.material = new Material(Shader.Find("Sprites/Default"));
        lr.startColor = Color.white;
        lr.endColor = Color.white;
    }

    void Update()
    {
        if (startPoint == null || endPoint == null) return;

        lr.SetPosition(0, startPoint.position);
        lr.SetPosition(1, endPoint.position);
    }
}