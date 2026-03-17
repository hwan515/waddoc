using UnityEngine;
using Unity.Robotics.ROSTCPConnector;
using RosMessageTypes.Geometry;

[RequireComponent(typeof(Rigidbody))]
public class CmdVelCarController : MonoBehaviour
{
    [Header("ROS")]
    public string cmdVelTopic = "/cmd_vel";

    [Header("Wheel Colliders")]
    public WheelCollider wcFL;
    public WheelCollider wcFR;
    public WheelCollider wcRL;
    public WheelCollider wcRR;

    [Header("Wheel Visual Roots (VIS_*)")]
    public Transform visFL;
    public Transform visFR;
    public Transform visRL;
    public Transform visRR;

    [Header("Wheel Visual Rotation Offsets")]
    public Vector3 visFLOffset = Vector3.zero;
    public Vector3 visFROffset = Vector3.zero;
    public Vector3 visRLOffset = Vector3.zero;
    public Vector3 visRROffset = Vector3.zero;

    [Header("Vehicle Geometry")]
    public float wheelBase = 2.39f;

    [Header("Command Limits")]
    public float maxSpeed = 2.5f;      // m/s
    public float maxAngular = 0.7f;    // rad/s

    [Header("Steering")]
    public float maxSteerAngleLowSpeed = 9f;
    public float maxSteerAngleHighSpeed = 4f;
    public float steerResponse = 5f;
    public bool invertSteering = true;

    [Header("Drive")]
    public bool invertDrive = true;    // 차량 실제 전방이 -Z면 true
    public float maxMotorTorque = 900f;
    public float brakeTorque = 4000f;
    public float idleBrakeTorque = 300f;

    [Header("Timeout")]
    public float cmdTimeout = 0.5f;

    [Header("RigidBody")]
    public bool useCustomCenterOfMass = true;
    public Vector3 customCenterOfMass = new Vector3(0f, -0.35f, 0f);
    public float rbMass = 1300f;
    public float rbLinearDamping = 0.02f;
    public float rbAngularDamping = 1.2f;

    [Header("WheelCollider Auto Setup")]
    public bool setupWheelCollidersOnStart = false;
    public float wheelMass = 30f;
    public float wheelDampingRate = 1.0f;
    public float suspensionDistance = 0.08f;
    public float forceAppPointDistance = 0.22f;
    public float suspensionSpring = 32000f;
    public float suspensionDamper = 6000f;
    public float suspensionTargetPosition = 0.5f;
    public float forwardStiffness = 1.4f;
    public float sidewaysStiffness = 2.0f;

    [Header("Debug")]
    public bool printDebug = false;

    private ROSConnection ros;
    private Rigidbody rb;

    private float cmdLinear = 0f;
    private float cmdAngular = 0f;
    private float lastCmdTime = -999f;
    private float currentSteerAngle = 0f;

    void Start()
    {
        rb = GetComponent<Rigidbody>();

        rb.mass = rbMass;
        rb.linearDamping = rbLinearDamping;
        rb.angularDamping = rbAngularDamping;
        rb.interpolation = RigidbodyInterpolation.Interpolate;
        rb.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;

        if (useCustomCenterOfMass)
        {
            rb.automaticCenterOfMass = false;
            rb.centerOfMass = customCenterOfMass;
        }

        if (setupWheelCollidersOnStart)
        {
            SetupWheelCollider(wcFL);
            SetupWheelCollider(wcFR);
            SetupWheelCollider(wcRL);
            SetupWheelCollider(wcRR);
        }

        ros = ROSConnection.GetOrCreateInstance();
        ros.Subscribe<TwistMsg>(cmdVelTopic, CmdVelCallback);
    }

    void FixedUpdate()
    {
        bool timedOut = (Time.time - lastCmdTime) > cmdTimeout;

        float linearCmd = timedOut ? 0f : cmdLinear;
        float angularCmd = timedOut ? 0f : cmdAngular;

        ApplyDrive(linearCmd, angularCmd);
        UpdateWheelVisuals();
    }

    void CmdVelCallback(TwistMsg msg)
    {
        cmdLinear = Mathf.Clamp((float)msg.linear.x, -maxSpeed, maxSpeed);
        cmdAngular = Mathf.Clamp((float)msg.angular.z, -maxAngular, maxAngular);
        lastCmdTime = Time.time;
    }

    void SetupWheelCollider(WheelCollider wc)
    {
        if (wc == null) return;

        wc.mass = wheelMass;
        wc.wheelDampingRate = wheelDampingRate;
        wc.suspensionDistance = suspensionDistance;
        wc.forceAppPointDistance = forceAppPointDistance;

        JointSpring js = wc.suspensionSpring;
        js.spring = suspensionSpring;
        js.damper = suspensionDamper;
        js.targetPosition = suspensionTargetPosition;
        wc.suspensionSpring = js;

        WheelFrictionCurve fwd = wc.forwardFriction;
        fwd.extremumSlip = 0.4f;
        fwd.extremumValue = 1f;
        fwd.asymptoteSlip = 0.8f;
        fwd.asymptoteValue = 0.5f;
        fwd.stiffness = forwardStiffness;
        wc.forwardFriction = fwd;

        WheelFrictionCurve side = wc.sidewaysFriction;
        side.extremumSlip = 0.2f;
        side.extremumValue = 1f;
        side.asymptoteSlip = 0.5f;
        side.asymptoteValue = 0.75f;
        side.stiffness = sidewaysStiffness;
        wc.sidewaysFriction = side;
    }

    void ApplyDrive(float linearCmd, float angularCmd)
    {
        float currentSpeed = Mathf.Abs(GetForwardSpeed());

        // 속도 높을수록 최대 조향각 감소
        float speedRatio = Mathf.Clamp01(currentSpeed / Mathf.Max(maxSpeed, 0.01f));
        float dynamicMaxSteer = Mathf.Lerp(maxSteerAngleLowSpeed, maxSteerAngleHighSpeed, speedRatio);

        float targetSteer = 0f;

        if (Mathf.Abs(linearCmd) > 0.03f)
        {
            targetSteer = Mathf.Rad2Deg * Mathf.Atan(
                (wheelBase * angularCmd) / Mathf.Max(Mathf.Abs(linearCmd), 0.05f)
            );
            targetSteer = Mathf.Clamp(targetSteer, -dynamicMaxSteer, dynamicMaxSteer);
        }

        if (invertSteering)
            targetSteer = -targetSteer;

        currentSteerAngle = Mathf.Lerp(
            currentSteerAngle,
            targetSteer,
            Time.fixedDeltaTime * steerResponse
        );

        if (wcFL != null) wcFL.steerAngle = currentSteerAngle;
        if (wcFR != null) wcFR.steerAngle = currentSteerAngle;

        float driveInput = invertDrive ? -linearCmd : linearCmd;

        float torqueFade = 1f - Mathf.Clamp01(currentSpeed / Mathf.Max(maxSpeed, 0.01f));
        float motorTorque = driveInput * maxMotorTorque * Mathf.Lerp(1.0f, 0.35f, 1f - torqueFade);

        if (Mathf.Abs(linearCmd) < 0.03f)
            motorTorque = 0f;

        // 후륜구동
        if (wcRL != null) wcRL.motorTorque = motorTorque;
        if (wcRR != null) wcRR.motorTorque = motorTorque;

        if (wcFL != null) wcFL.motorTorque = 0f;
        if (wcFR != null) wcFR.motorTorque = 0f;

        float appliedBrake = 0f;

        if (Mathf.Abs(linearCmd) < 0.03f)
            appliedBrake = brakeTorque;
        else if (Mathf.Abs(angularCmd) > 0.2f)
            appliedBrake = idleBrakeTorque;

        if (wcFL != null) wcFL.brakeTorque = appliedBrake;
        if (wcFR != null) wcFR.brakeTorque = appliedBrake;
        if (wcRL != null) wcRL.brakeTorque = appliedBrake;
        if (wcRR != null) wcRR.brakeTorque = appliedBrake;

        if (printDebug)
        {
            Debug.Log(
                $"linear={linearCmd:F2}, angular={angularCmd:F2}, steer={currentSteerAngle:F2}, " +
                $"torque={motorTorque:F2}, speed={currentSpeed:F2}"
            );
        }
    }

    void UpdateWheelVisuals()
    {
        SyncWheel(wcFL, visFL, visFLOffset);
        SyncWheel(wcFR, visFR, visFROffset);
        SyncWheel(wcRL, visRL, visRLOffset);
        SyncWheel(wcRR, visRR, visRROffset);
    }

    void SyncWheel(WheelCollider wc, Transform vis, Vector3 offsetEuler)
    {
        if (wc == null || vis == null) return;

        Vector3 pos;
        Quaternion rot;
        wc.GetWorldPose(out pos, out rot);

        vis.position = pos;
        vis.rotation = rot * Quaternion.Euler(offsetEuler);
    }

    float GetForwardSpeed()
    {
        return Vector3.Dot(rb.linearVelocity, GetVehicleForward());
    }

    Vector3 GetVehicleForward()
    {
        return invertDrive ? -transform.forward : transform.forward;
    }

    float GetUphillAngle(float linearCmd)
    {
        if (!enableHillAssist || Mathf.Abs(linearCmd) < 0.02f)
            return 0f;

        Vector3 driveForward = GetVehicleForward();

        if (linearCmd < 0f)
            driveForward = -driveForward;

        float uphillComponent = Mathf.Clamp(driveForward.y, -1f, 1f);
        return Mathf.Max(0f, Mathf.Asin(uphillComponent) * Mathf.Rad2Deg);
    }

    int CountGrounded(WheelCollider a, WheelCollider b)
    {
        int c = 0;
        if (a != null && a.isGrounded) c++;
        if (b != null && b.isGrounded) c++;
        return c;
    }


    void PrintWheelDebug()
    {
        bool gFL = wcFL != null && wcFL.isGrounded;
        bool gFR = wcFR != null && wcFR.isGrounded;
        bool gRL = wcRL != null && wcRL.isGrounded;
        bool gRR = wcRR != null && wcRR.isGrounded;

        float rpmFL = wcFL != null ? wcFL.rpm : 0f;
        float rpmFR = wcFR != null ? wcFR.rpm : 0f;
        float rpmRL = wcRL != null ? wcRL.rpm : 0f;
        float rpmRR = wcRR != null ? wcRR.rpm : 0f;

        if (printGroundedDebug || printDebug)
        {
            Debug.Log(
                $"Grounded FL:{gFL} FR:{gFR} RL:{gRL} RR:{gRR} | " +
                $"RPM FL:{rpmFL:F1} FR:{rpmFR:F1} RL:{rpmRL:F1} RR:{rpmRR:F1} | " +
                $"Vel:{rb.linearVelocity.magnitude:F2} | ForwardSpeed:{GetForwardSpeed():F2}"
            );
        }
    }
}