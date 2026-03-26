using System.Collections.Generic;
using UnityEngine;

public class MapImporter : MonoBehaviour
{
    [Header("JSON")]
    public TextAsset jsonFile;

    [Header("Gizmos")]
    public bool drawWaypoints = true;
    public bool drawConnections = true;

    public float sphereSize = 5.0f;
    public Color waypointColor = Color.red;
    public Color lineColor = Color.yellow;

    private WaypointList cachedData;

    [System.Serializable]
    public class Waypoint
    {
        public string id;
        public float x;
        public float y;
        public float z;
        public string[] connected_to;
    }

    [System.Serializable]
    public class WaypointList
    {
        public Waypoint[] waypoints;
    }

    void Start()
    {
        LoadJson();
    }

    [ContextMenu("Load JSON")]
    public void LoadJson()
    {
        if (jsonFile == null)
        {
            Debug.LogError("[MapImporter] jsonFile is null");
            return;
        }

        cachedData = JsonUtility.FromJson<WaypointList>(jsonFile.text);

        if (cachedData == null || cachedData.waypoints == null)
        {
            Debug.LogError("[MapImporter] parse 실패");
            return;
        }

        Debug.Log($"[MapImporter] waypoint 개수: {cachedData.waypoints.Length}");
    }

    void OnDrawGizmos()
    {
        if (cachedData == null || cachedData.waypoints == null)
            return;

        HashSet<string> drawn = new HashSet<string>();

        foreach (var wp in cachedData.waypoints)
        {
            Vector3 pos = new Vector3(wp.x, wp.y, wp.z);

            // 🔴 waypoint 표시
            if (drawWaypoints)
            {
                Gizmos.color = waypointColor;
                Gizmos.DrawSphere(pos, sphereSize);
            }

            // 🟡 연결선 표시
            if (drawConnections && wp.connected_to != null)
            {
                foreach (var targetId in wp.connected_to)
                {
                    var target = FindWaypoint(targetId);
                    if (target == null) continue;

                    string key = MakeKey(wp.id, targetId);
                    if (drawn.Contains(key)) continue;

                    Vector3 to = new Vector3(target.x, target.y, target.z);

                    Gizmos.color = lineColor;
                    Gizmos.DrawLine(pos, to);

                    drawn.Add(key);
                }
            }
        }
    }

    Waypoint FindWaypoint(string id)
    {
        foreach (var wp in cachedData.waypoints)
        {
            if (wp.id == id)
                return wp;
        }
        return null;
    }

    string MakeKey(string a, string b)
    {
        return string.CompareOrdinal(a, b) < 0 ? $"{a}|{b}" : $"{b}|{a}";
    }
}