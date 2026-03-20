using UnityEngine;

public class SimpleCarController : MonoBehaviour
{
    public WheelCollider wcFL;
    public WheelCollider wcFR;
    public WheelCollider wcRL;
    public WheelCollider wcRR;

    public float motorPower = 1500f;
    public float maxSteer = 30f;

    void FixedUpdate()
    {
        float throttle = Input.GetAxis("Vertical");
        float steer = Input.GetAxis("Horizontal");

        float motor = throttle * motorPower;
        float steerAngle = steer * maxSteer;

        wcFL.steerAngle = steerAngle;
        wcFR.steerAngle = steerAngle;

        wcRL.motorTorque = motor;
        wcRR.motorTorque = motor;
    }
}