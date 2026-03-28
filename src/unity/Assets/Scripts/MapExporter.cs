using UnityEngine;
using System.Collections.Generic;
using System.IO;

[System.Serializable]
public class WaypointNode
{
    public string id;
    public float x;
    public float y;
    public float z;
    public List<string> connected_to;
}

[System.Serializable]
public class TopologicalMap
{
    public List<WaypointNode> waypoints = new List<WaypointNode>();
}

public class MapExporter : MonoBehaviour
{
    [ContextMenu("Export Waypoints to JSON")]
    public void ExportToJson()
    {
        TopologicalMap map = new TopologicalMap();
        Waypoint[] allWaypoints = FindObjectsOfType<Waypoint>();

        foreach (Waypoint wp in allWaypoints)
        {
            WaypointNode node = new WaypointNode();
            node.id = wp.gameObject.name;
            node.x = wp.transform.position.x;
            node.y = wp.transform.position.y;
            node.z = wp.transform.position.z;

            node.connected_to = new List<string>();
            if (wp.connectedNodes != null)
            {
                foreach (Waypoint connected in wp.connectedNodes)
                {
                    if (connected != null)
                    {
                        node.connected_to.Add(connected.gameObject.name);
                    }
                }
            }
            map.waypoints.Add(node);
        }

        string json = JsonUtility.ToJson(map, true);
        string path = Path.Combine(Application.dataPath, "TopologicalMap.json");
        File.WriteAllText(path, json);

        Debug.Log("맵 데이터 추출 완료. 저장 경로: " + path);
    }
}
