using UnityEngine;
using System.Collections.Generic;

public class Waypoint : MonoBehaviour
{
    [Header("연결된 갈림길 노드들")]
    public List<Waypoint> connectedNodes;

    
    private void OnDrawGizmos()
    {
        Gizmos.color = Color.red;
        Gizmos.DrawSphere(transform.position, 0.5f);

        if (connectedNodes != null)
        {
            Gizmos.color = Color.blue;
            foreach (Waypoint node in connectedNodes)
            {
                if (node != null)
                {
                    Gizmos.DrawLine(transform.position, node.transform.position);
                }
            }
        }
    }
}
