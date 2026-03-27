using System.Collections.Generic;
using UnityEngine;

public class WaypointPathCamera : MonoBehaviour
{
    [Header("Path")]
    public List<Transform> waypoints = new List<Transform>();

    [Header("Movement")]
    public float moveSpeed = 8f;
    public float smoothTime = 0.25f;
    public float rotationSpeed = 4f;
    public float reachDistance = 0.3f;
    public bool loop = false;

    [Header("Stop Option")]
    public float waitTimeAtPoint = 0f;
    public bool useWaypointRotation = true;

    private int currentIndex = 0;
    private Vector3 velocity = Vector3.zero;
    private bool isWaiting = false;
    private float waitTimer = 0f;

    void Update()
    {
        if (waypoints == null || waypoints.Count == 0) return;
        if (currentIndex >= waypoints.Count) return;

        Transform targetPoint = waypoints[currentIndex];

        if (isWaiting)
        {
            waitTimer += Time.deltaTime;
            UpdateRotation(targetPoint);

            if (waitTimer >= waitTimeAtPoint)
            {
                waitTimer = 0f;
                isWaiting = false;
                currentIndex++;

                if (loop && currentIndex >= waypoints.Count)
                    currentIndex = 0;
            }
            return;
        }

        transform.position = Vector3.SmoothDamp(
            transform.position,
            targetPoint.position,
            ref velocity,
            smoothTime,
            moveSpeed
        );

        UpdateRotation(targetPoint);

        if (Vector3.Distance(transform.position, targetPoint.position) <= reachDistance)
        {
            if (waitTimeAtPoint > 0f)
            {
                isWaiting = true;
            }
            else
            {
                currentIndex++;
                if (loop && currentIndex >= waypoints.Count)
                    currentIndex = 0;
            }
        }
    }

    void UpdateRotation(Transform targetPoint)
    {
        Quaternion targetRotation;

        if (useWaypointRotation)
        {
            targetRotation = targetPoint.rotation;
        }
        else
        {
            Vector3 direction = targetPoint.position - transform.position;
            if (direction.sqrMagnitude < 0.001f) return;
            targetRotation = Quaternion.LookRotation(direction.normalized, Vector3.up);
        }

        transform.rotation = Quaternion.Slerp(
            transform.rotation,
            targetRotation,
            rotationSpeed * Time.deltaTime
        );
    }
}