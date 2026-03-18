using UnityEngine;
using Unity.Robotics.ROSTCPConnector;
using RosMessageTypes.Geometry;

[RequireComponent(typeof(Rigidbody))]
public class CmdVelCarController : MonoBehaviour
{
    [Header("ROS")]
    public string cmdVelTopic = "/cmd_vel";

    [Header("=== TOP LIMIT SETTINGS ===")]
    public float inputLinearLimit = 10.0f;       // ROS linear.x 최대 허용
    public float inputAngularLimit = 5.0f;       // ROS angular.z 최대 허용

    [Header("=== VEHICLE SPEED / TURN RESPONSE ===")]
    public float maxMotorTorque = 9000f;        // 기본 최대 토크
    public float launchTorqueBoost = 1.35f;      // 출발 시 추가 토크
    public float maxSteerAngleLowSpeed = 24f;    // 저속 최대 조향각
    public float maxSteerAngleHighSpeed = 12f;   // 고속 최대 조향각
    public float steerResponse = 8f;            // 조향 반응 속도

    [Header("=== TURN / PIVOT TUNING ===")]
    public bool slowDownWhenTurning = true;
    public float turnSlowdownStart = 0.25f;
    public float turnSlowdownMinFactor = 0.88f;
    public float pivotBoostAngular = 0.35f;
    public float pivotSpeedLimit = 2.2f;
    public float pivotSteerMultiplier = 1.25f;

    [Header("=== HILL CLIMB ASSIST ===")]
    public bool enableHillAssist = true;
    public float uphillAngleForFullAssist = 16f;
    public float uphillTorqueMultiplier = 2.0f;      // 언덕 토크 강화
    public float uphillFrontDriveBias = 0.45f;       // 언덕에서 앞바퀴도 구동
    public float uphillTurnSlowdownMinFactor = 0.96f;

    [Header("=== DRIVE MODE ===")]
    public bool rearWheelDrive = true;
    public bool frontWheelDrive = false;
    public bool enableAutoAWDOnHill = true;

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
    public float wheelBase = 2.7f;

    [Header("Drive Direction")]
    public bool invertSteering = true;
    public bool invertDrive = true;   // 차량 실제 전방이 -Z면 true

    [Header("Brake")]
    public float brakeTorque = 3000f;
    public float holdBrakeTorque = 3000f;       // timeout/정지 유지용
    public float idleBrakeTorque = 800f;

    [Header("Timeout")]
    public float cmdTimeout = 0.5f;

    [Header("RigidBody")]
    public bool useCustomCenterOfMass = true;
    public Vector3 customCenterOfMass = new Vector3(0f, -0.35f, 0f);
    public float rbMass = 700f;
    public float rbLinearDamping = 0.02f;
    public float rbAngularDamping = 1.2f;

    [Header("WheelCollider Auto Setup")]
    public bool setupWheelCollidersOnStart = false;
    public float wheelRadius = 0.30f;
    public float wheelMass = 30f;
    public float wheelDampingRate = 1.0f;
    public float suspensionDistance = 0.18f;
    public float forceAppPointDistance = 0.08f;
    public float suspensionSpring = 38000f;
    public float suspensionDamper = 7000f;
    public float suspensionTargetPosition = 0.50f;
    public float forwardStiffness = 1.5f;
    public float sidewaysStiffness = 2.0f;

    [Header("Safety / Limits")]
    public bool clampRigidBodySpeed = false;
    public float maxBodySpeed = 12f;

    [Header("Debug")]
    public bool printDebug = false;
    public bool printGroundedDebug = false;
    public float debugInterval = 0.25f;

    private ROSConnection ros;
    private Rigidbody rb;

    private float cmdLinear = 0f;
    private float cmdAngular = 0f;
    private float lastCmdTime = -999f;
    private float currentSteerAngle = 0f;
    private float lastDebugTime = -999f;

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

        if (printDebug)
        {
            Debug.Log(
                $"CmdVelCarController started | mass={rbMass}, maxMotorTorque={maxMotorTorque}, " +
                $"inputLinearLimit={inputLinearLimit}, inputAngularLimit={inputAngularLimit}"
            );
        }
    }

    void FixedUpdate()
    {
        bool timedOut = (Time.time - lastCmdTime) > cmdTimeout;

        float linearCmd = timedOut ? 0f : cmdLinear;
        float angularCmd = timedOut ? 0f : cmdAngular;

        ApplyDrive(linearCmd, angularCmd, timedOut);

        if (clampRigidBodySpeed && rb.linearVelocity.magnitude > maxBodySpeed)
        {
            rb.linearVelocity = rb.linearVelocity.normalized * maxBodySpeed;
        }

        UpdateWheelVisuals();

        if ((printDebug || printGroundedDebug) && Time.time - lastDebugTime > debugInterval)
        {
            lastDebugTime = Time.time;
            PrintWheelDebug();
        }
    }

    void CmdVelCallback(TwistMsg msg)
    {
        cmdLinear = Mathf.Clamp((float)msg.linear.x, -inputLinearLimit, inputLinearLimit);
        cmdAngular = Mathf.Clamp((float)msg.angular.z, -inputAngularLimit, inputAngularLimit);
        lastCmdTime = Time.time;
    }

    void SetupWheelCollider(WheelCollider wc)
    {
        if (wc == null) return;

        wc.radius = wheelRadius;
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
        fwd.asymptoteValue = 0.75f;
        fwd.stiffness = forwardStiffness;
        wc.forwardFriction = fwd;

        WheelFrictionCurve side = wc.sidewaysFriction;
        side.extremumSlip = 0.2f;
        side.extremumValue = 1f;
        side.asymptoteSlip = 0.5f;
        side.asymptoteValue = 0.85f;
        side.stiffness = sidewaysStiffness;
        wc.sidewaysFriction = side;

        wc.motorTorque = 0f;
        wc.brakeTorque = 0f;
        wc.steerAngle = 0f;
    }

    void ApplyDrive(float linearCmd, float angularCmd, bool timedOut)
    {
        float signedForwardSpeed = GetForwardSpeed();
        float currentSpeedAbs = Mathf.Abs(signedForwardSpeed);

        float uphillAngle = GetUphillAngle(linearCmd);
        float uphillAssist = enableHillAssist
            ? Mathf.InverseLerp(0f, Mathf.Max(1f, uphillAngleForFullAssist), uphillAngle)
            : 0f;

        // 속도에 따른 조향 제한
        float speedRatio = Mathf.Clamp01(currentSpeedAbs / Mathf.Max(inputLinearLimit, 0.01f));
        float dynamicMaxSteer = Mathf.Lerp(maxSteerAngleLowSpeed, maxSteerAngleHighSpeed, speedRatio);

        float absAngular = Mathf.Abs(angularCmd);
        bool pivotMode = absAngular > pivotBoostAngular;

        if (pivotMode)
            dynamicMaxSteer *= pivotSteerMultiplier;

        // cmd_vel -> steer angle
        float targetSteer = 0f;
        if (Mathf.Abs(linearCmd) > 0.02f || absAngular > 0.08f)
        {
            float steerDenom = Mathf.Max(Mathf.Abs(linearCmd), 0.12f);
            targetSteer = Mathf.Rad2Deg * Mathf.Atan((wheelBase * angularCmd) / steerDenom);
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

        // 회전 시 선속도 감속
        float effectiveLinearCmd = linearCmd;
        if (slowDownWhenTurning)
        {
            float t = Mathf.InverseLerp(turnSlowdownStart, inputAngularLimit, absAngular);
            float turnMinFactor = Mathf.Lerp(
                turnSlowdownMinFactor,
                uphillTurnSlowdownMinFactor,
                uphillAssist
            );
            float slowdown = Mathf.Lerp(1f, turnMinFactor, t);
            effectiveLinearCmd *= slowdown;
        }

        // pivot 중 linear 너무 과하면 제한
        if (pivotMode)
        {
            effectiveLinearCmd = Mathf.Clamp(effectiveLinearCmd, -pivotSpeedLimit, pivotSpeedLimit);
        }

        // 구동 방향 반전
        float driveInput = invertDrive ? -effectiveLinearCmd : effectiveLinearCmd;

        // 낮은 속도에서 출발 보정
        float launchAssist = Mathf.Lerp(launchTorqueBoost, 1f, Mathf.Clamp01(currentSpeedAbs / 2.0f));

        // 속도 올라갈수록 토크 자연 감쇠
        float torqueFade = 1f - Mathf.Clamp01(currentSpeedAbs / Mathf.Max(inputLinearLimit, 0.01f));
        float speedTorqueFactor = Mathf.Lerp(0.45f, 1.0f, torqueFade);

        float motorTorque = driveInput * maxMotorTorque * launchAssist * speedTorqueFactor;
        motorTorque *= Mathf.Lerp(1f, uphillTorqueMultiplier, uphillAssist);

        if (Mathf.Abs(effectiveLinearCmd) < 0.02f)
            motorTorque = 0f;

        // 언덕에서 auto AWD
        bool useFrontDriveNow = frontWheelDrive;
        bool useRearDriveNow = rearWheelDrive;

        if (enableAutoAWDOnHill && uphillAssist > 0.05f)
        {
            useFrontDriveNow = true;
            useRearDriveNow = true;
        }

        float frontDriveBias = 0f;
        float rearDriveBias = 0f;

        if (useFrontDriveNow && useRearDriveNow)
        {
            frontDriveBias = Mathf.Lerp(0.5f, uphillFrontDriveBias, uphillAssist);
            rearDriveBias = 1f - frontDriveBias;
        }
        else if (useFrontDriveNow)
        {
            frontDriveBias = 1f;
            rearDriveBias = 0f;
        }
        else if (useRearDriveNow)
        {
            frontDriveBias = 0f;
            rearDriveBias = 1f;
        }

        // 접지 부족 시 전달 토크 줄이기보다, 접지된 축 위주로 유지
        int frontGrounded = CountGrounded(wcFL, wcFR);
        int rearGrounded = CountGrounded(wcRL, wcRR);

        float frontTorque = 0f;
        float rearTorque = 0f;

        if (frontGrounded > 0) frontTorque = motorTorque * frontDriveBias;
        if (rearGrounded > 0) rearTorque = motorTorque * rearDriveBias;

        // 둘 다 접지 안 되어 있으면 그냥 원래 배분
        if (frontGrounded == 0 && rearGrounded == 0)
        {
            frontTorque = motorTorque * frontDriveBias;
            rearTorque = motorTorque * rearDriveBias;
        }

        ApplyMotorTorque(frontTorque, rearTorque);

        // 브레이크
        float appliedBrake = 0f;

        if (timedOut)
        {
            appliedBrake = holdBrakeTorque;
        }
        else if (Mathf.Abs(linearCmd) < 0.02f && absAngular < 0.05f)
        {
            appliedBrake = brakeTorque;
        }
        else if (Mathf.Abs(linearCmd) < 0.02f && absAngular >= 0.05f)
        {
            // 제자리 조향/미세 조향 시 브레이크 최대한 해제
            appliedBrake = idleBrakeTorque;
        }
        else
        {
            appliedBrake = 0f;
        }

        ApplyBrakeTorque(appliedBrake);
    }

    void ApplyMotorTorque(float frontTorque, float rearTorque)
    {
        if (wcFL != null) wcFL.motorTorque = frontTorque * 0.5f;
        if (wcFR != null) wcFR.motorTorque = frontTorque * 0.5f;
        if (wcRL != null) wcRL.motorTorque = rearTorque * 0.5f;
        if (wcRR != null) wcRR.motorTorque = rearTorque * 0.5f;
    }

    void ApplyBrakeTorque(float brake)
    {
        if (wcFL != null) wcFL.brakeTorque = brake;
        if (wcFR != null) wcFR.brakeTorque = brake;
        if (wcRL != null) wcRL.brakeTorque = brake;
        if (wcRR != null) wcRR.brakeTorque = brake;
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