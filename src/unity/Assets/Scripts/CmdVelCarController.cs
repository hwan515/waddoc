using UnityEngine;
using Unity.Robotics.ROSTCPConnector;
using RosMessageTypes.Geometry;

[RequireComponent(typeof(Rigidbody))]
public class CmdVelCarController : MonoBehaviour
{
    [Header("ROS")]
    public string cmdVelTopic = "/cmd_vel";  
    // ROS에서 Twist(cmd_vel)를 구독할 토픽 이름
    // 보통 linear.x = 전진/후진 명령, angular.z = 회전 명령으로 사용

    [Header("=== TOP LIMIT SETTINGS ===")]
    public float inputLinearLimit = 10.0f;       
    // ROS에서 들어오는 linear.x 입력값의 최대 절대값
    // 예: 10이면 -10 ~ +10 범위까지만 허용하고 그 이상은 잘라냄

    public float inputAngularLimit = 5.0f;       
    // ROS에서 들어오는 angular.z 입력값의 최대 절대값
    // 예: 5이면 -5 ~ +5 범위까지만 허용

    [Header("=== VEHICLE SPEED / TURN RESPONSE ===")]
    public float maxMotorTorque = 9000f;        
    // 바퀴에 전달할 수 있는 기본 최대 구동 토크
    // 값이 크면 가속이 강해지고 언덕/출발 힘이 좋아지지만 과하면 휠스핀이나 튐 발생 가능

    public float launchTorqueBoost = 1.35f;      
    // 정지 상태 또는 아주 저속에서 출발할 때 토크를 추가로 증폭하는 계수
    // 출발이 굼뜰 때 올리고, 튀어나가면 낮춤

    public float maxSteerAngleLowSpeed = 35f;    
    // 저속일 때 허용하는 최대 조향각(도)
    // 낮은 속도에서는 큰 핸들각을 허용해서 회전 반경을 줄임

    public float maxSteerAngleHighSpeed = 35f;   
    // 고속일 때 허용하는 최대 조향각(도)
    // 일반적으로 고속에서는 값을 낮춰서 급조향을 막음
    // 현재는 저속/고속 둘 다 35라 속도에 따른 조향 제한 차이가 거의 없음

    public float steerResponse = 6f;            
    // 현재 조향각이 목표 조향각으로 따라가는 속도
    // 값이 크면 핸들이 더 빨리 꺾이고, 작으면 부드럽지만 반응이 느려짐

    [Header("=== TURN / PIVOT TUNING ===")]
    public bool slowDownWhenTurning = true;
    // 회전 명령이 커질수록 전진 속도를 자동으로 줄일지 여부
    // true면 코너에서 덜 밀리고 안정적이지만 느려짐

    public float turnSlowdownStart = 0.25f;
    // angular.z 절대값이 이 값 이상부터 회전 감속을 시작
    // 너무 낮으면 조금만 꺾어도 속도가 줄고, 너무 높으면 급회전에서도 감속이 늦음

    public float turnSlowdownMinFactor = 0.88f;
    // 회전 시 줄어드는 선속도의 최소 배율
    // 예: 0.88이면 가장 많이 감속되어도 원래 속도의 88%까지 유지
    // 1.0에 가까울수록 감속이 약함, 더 작을수록 코너에서 더 느려짐

    public float pivotBoostAngular = 0.35f;
    // angular.z 절대값이 이 값보다 크면 "강한 회전" 또는 pivot 성향 회전으로 판단
    // 이때 조향각 확대나 속도 제한 같은 pivot 보정이 들어감

    public float pivotSpeedLimit = 2.2f;
    // pivot 모드일 때 linear.x를 이 값 이하로 제한
    // 회전 명령이 큰 상태에서 너무 빠르게 전진하지 못하게 막음

    public float pivotSteerMultiplier = 1.25f;
    // pivot 모드에서 최대 조향각을 몇 배로 늘릴지 결정
    // 값이 크면 더 확 꺾이지만 과하면 불안정할 수 있음

    [Header("=== HILL CLIMB ASSIST ===")]
    public bool enableHillAssist = true;
    // 언덕에서 토크 보정, AWD 자동 전환 같은 보조 기능을 켤지 여부

    public float uphillAngleForFullAssist = 16f;
    // 언덕 경사각이 이 값에 도달하면 언덕 보조를 100% 적용
    // 예: 16도 이상이면 uphillTorqueMultiplier가 거의 최대 반영됨

    public float uphillTorqueMultiplier = 2.0f;      
    // 언덕 오를 때 토크를 얼마나 더 증폭할지 결정
    // 2.0이면 최대 보조 시 평지 대비 2배 수준까지 토크 강화

    public float uphillFrontDriveBias = 0.45f;       
    // 언덕에서 AWD가 켜졌을 때 앞축으로 보내는 토크 비율
    // 예: 0.45면 앞 45%, 뒤 55%
    // 너무 높으면 앞바퀴 위주, 너무 낮으면 뒤바퀴 위주

    public float uphillTurnSlowdownMinFactor = 0.96f;
    // 언덕에서는 회전 감속을 덜 걸기 위한 최소 감속 배율
    // 평지(turnSlowdownMinFactor)보다 크게 두면 언덕에서 속도 유지가 더 잘 됨

    [Header("=== DRIVE MODE ===")]
    public bool rearWheelDrive = true;
    // 평상시 뒷바퀴 구동 여부
    // true면 후륜구동 성향

    public bool frontWheelDrive = false;
    // 평상시 앞바퀴 구동 여부
    // true면 전륜구동 성향

    public bool enableAutoAWDOnHill = true;
    // 언덕에서 자동으로 앞/뒤 모두 구동(AWD)으로 전환할지 여부
    // 후륜만으로 언덕을 못 오를 때 유용

    [Header("Wheel Colliders")]
    public WheelCollider wcFL;
    // Front Left WheelCollider
    // 앞왼쪽 실제 물리 바퀴

    public WheelCollider wcFR;
    // Front Right WheelCollider
    // 앞오른쪽 실제 물리 바퀴

    public WheelCollider wcRL;
    // Rear Left WheelCollider
    // 뒤왼쪽 실제 물리 바퀴

    public WheelCollider wcRR;
    // Rear Right WheelCollider
    // 뒤오른쪽 실제 물리 바퀴

    [Header("Wheel Visual Roots (VIS_*)")]
    public Transform visFL;
    // 앞왼쪽 바퀴의 시각 모델 Transform
    // WheelCollider 위치/회전을 받아서 보이는 바퀴 메시에 반영

    public Transform visFR;
    // 앞오른쪽 바퀴 시각 모델

    public Transform visRL;
    // 뒤왼쪽 바퀴 시각 모델

    public Transform visRR;
    // 뒤오른쪽 바퀴 시각 모델

    [Header("Wheel Visual Rotation Offsets")]
    public Vector3 visFLOffset = Vector3.zero;
    // 앞왼쪽 바퀴 시각 모델 회전 보정값(Euler)
    // 모델 축 방향이 WheelCollider와 안 맞을 때 사용

    public Vector3 visFROffset = Vector3.zero;
    // 앞오른쪽 바퀴 시각 모델 회전 보정값

    public Vector3 visRLOffset = Vector3.zero;
    // 뒤왼쪽 바퀴 시각 모델 회전 보정값

    public Vector3 visRROffset = Vector3.zero;
    // 뒤오른쪽 바퀴 시각 모델 회전 보정값

    [Header("Vehicle Geometry")]
    public float wheelBase = 2.7f;
    // 앞축과 뒤축 사이 거리
    // cmd_vel의 angular.z를 실제 조향각으로 환산할 때 사용
    // 차체가 길수록 같은 회전 명령에 필요한 조향각 계산이 달라짐

    [Header("Drive Direction")]
    public bool invertSteering = false;
    // 조향 방향 반전 여부
    // true면 좌회전 명령이 우회전처럼 들어가는 상황에서 반대로 뒤집을 때 사용

    public bool invertDrive = true;   
    // 구동 방향 반전 여부
    // 차량 모델의 실제 전방이 Unity 기준 +Z가 아니라 -Z면 true로 사용
    // 전진 명령인데 뒤로 가는 경우 이 값을 점검

    [Header("Brake")]
    public float brakeTorque = 6000f;
    // 정지 명령이 들어왔을 때 일반적으로 거는 브레이크 토크
    // 값이 크면 빨리 멈추지만 너무 크면 급정지 느낌이 강해짐

    public float holdBrakeTorque = 6000f;       
    // cmd_vel timeout 또는 완전 정지 유지 시 사용하는 브레이크 토크
    // 언덕에서 밀림 방지용으로 중요

    public float idleBrakeTorque = 100f;
    // 거의 멈춰 있는 상태에서 미세하게만 잡는 브레이크
    // 제자리 조향 시 바퀴가 너무 잠기지 않도록 약하게 유지

    [Header("Timeout")]
    public float cmdTimeout = 0.5f;
    // 마지막 cmd_vel 수신 후 이 시간 이상 지나면 명령 끊김으로 판단
    // timeout 시 linear/angluar를 0으로 보고 holdBrakeTorque 적용

    [Header("RigidBody")]
    public bool useCustomCenterOfMass = true;
    // Rigidbody의 무게중심을 수동 설정할지 여부
    // 차가 쉽게 뒤집히거나 들뜨면 보통 켜고 아래로 내림

    public Vector3 customCenterOfMass = new Vector3(0f, -0.35f, 0f);
    // 사용자 지정 무게중심 위치
    // y를 더 낮추면 전복 방지에 도움, 너무 낮으면 움직임이 부자연스러울 수 있음

    public float rbMass = 700f;
    // 차체 Rigidbody 질량
    // 너무 가벼우면 튀고, 너무 무거우면 둔해짐

    public float rbLinearDamping = 0.02f;
    // 직선 운동 감쇠
    // 높이면 속도가 더 빨리 줄고, 낮으면 관성으로 더 오래 감

    public float rbAngularDamping = 0.12f;
    // 회전 운동 감쇠
    // 높이면 차체가 덜 휙휙 돌고 더 안정적
    // 너무 높으면 회전 반응이 둔해질 수 있음

    [Header("WheelCollider Auto Setup")]
    public bool setupWheelCollidersOnStart = false;
    // Start()에서 WheelCollider 파라미터를 자동으로 덮어쓸지 여부
    // true면 아래 값들로 강제 세팅, false면 인스펙터에 각 WheelCollider가 가진 현재 값 유지

    public float wheelRadius = 0.30f;
    // 바퀴 반지름
    // 시각적 바퀴 크기와 실제 WheelCollider 반지름이 다르면 접지/떠보임 문제 발생

    public float wheelMass = 30f;
    // 바퀴 하나의 질량
    // 너무 작으면 튀고, 너무 크면 반응이 무거워질 수 있음

    public float wheelDampingRate = 0.08f;
    // 바퀴 회전 감쇠
    // 바퀴가 헛도는 정도나 rpm 감소 성향에 영향

    public float suspensionDistance = 0.25f;
    // 서스펜션이 위아래로 움직일 수 있는 최대 거리
    // 너무 짧으면 딱딱하고, 너무 길면 차가 출렁이거나 뜰 수 있음

    public float forceAppPointDistance = 0.08f;
    // WheelCollider 힘이 적용되는 지점의 높이 관련 값
    // 차의 롤링/접지감에 영향

    public float suspensionSpring = 38000f;
    // 서스펜션 스프링 강성
    // 높이면 단단해지고 차체가 덜 내려앉음
    // 너무 높으면 통통 튈 수 있음

    public float suspensionDamper = 7000f;
    // 서스펜션 감쇠
    // 출렁임을 얼마나 빨리 잡는지 결정
    // 낮으면 계속 흔들리고, 높으면 움직임이 딱딱해짐

    public float suspensionTargetPosition = 0.50f;
    // 서스펜션 기본 압축 위치(0~1)
    // 0이면 많이 눌린 상태, 1이면 많이 펴진 상태에 가까움

    public float forwardStiffness = 1.5f;
    // 전후 방향 마찰 강성
    // 가속/감속/구동 시 미끄러짐 정도에 영향

    public float sidewaysStiffness = 2.0f;
    // 좌우 방향 마찰 강성
    // 코너링 시 옆으로 미끄러지는 정도에 영향
    // 너무 낮으면 쉽게 미끄러지고, 너무 높으면 급격하게 버티는 느낌

    [Header("Safety / Limits")]
    public bool clampRigidBodySpeed = false;
    // Rigidbody의 실제 속도를 강제로 제한할지 여부
    // 물리 폭주 방지용

    public float maxBodySpeed = 12f;
    // clampRigidBodySpeed가 true일 때 허용하는 최대 실제 차체 속도
    // 이 값을 넘으면 velocity를 강제로 잘라냄

    [Header("Debug")]
    public bool printDebug = false;
    // 일반 디버그 로그 출력 여부
    // 시작 정보, 속도, RPM 등 로그 출력

    public bool printGroundedDebug = false;
    // 각 바퀴 접지 상태 중심의 디버그 로그 출력 여부

    public float debugInterval = 0.25f;
    // 디버그 로그 출력 간격(초)
    // 너무 짧으면 콘솔이 과하게 쌓임

    private ROSConnection ros;
    // ROS-TCP Connector 인스턴스 참조

    private Rigidbody rb;
    // 현재 차량의 Rigidbody 참조

    private float cmdLinear = 0f;
    // 최근 수신한 linear.x 명령 저장값

    private float cmdAngular = 0f;
    // 최근 수신한 angular.z 명령 저장값

    private float lastCmdTime = -999f;
    // 마지막 cmd_vel 받은 시간
    // timeout 판정용

    private float currentSteerAngle = 0f;
    // 실제 현재 적용 중인 조향각
    // targetSteer로 바로 점프하지 않고 Lerp로 부드럽게 따라감

    private float lastDebugTime = -999f;
    // 마지막 디버그 로그 출력 시간

    void Start()
    {
        rb = GetComponent<Rigidbody>();

        rb.mass = rbMass;
        // Rigidbody 질량 설정

        rb.linearDamping = rbLinearDamping;
        // 직선 운동 감쇠 설정

        rb.angularDamping = rbAngularDamping;
        // 회전 운동 감쇠 설정

        rb.interpolation = RigidbodyInterpolation.Interpolate;
        // 프레임 사이 보간 적용
        // 시각적으로 더 부드러워 보임

        rb.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;
        // 빠르게 움직일 때 충돌 누락 방지용

        if (useCustomCenterOfMass)
        {
            rb.automaticCenterOfMass = false;
            // 자동 무게중심 계산 비활성화

            rb.centerOfMass = customCenterOfMass;
            // 수동 무게중심 적용
        }

        if (setupWheelCollidersOnStart)
        {
            SetupWheelCollider(wcFL);
            SetupWheelCollider(wcFR);
            SetupWheelCollider(wcRL);
            SetupWheelCollider(wcRR);
            // 각 WheelCollider에 반지름, 서스펜션, 마찰값 등을 일괄 적용
        }

        ros = ROSConnection.GetOrCreateInstance();
        // ROS 연결 객체 가져오기

        ros.Subscribe<TwistMsg>(cmdVelTopic, CmdVelCallback);
        // cmdVelTopic으로 들어오는 Twist 메시지 구독 시작

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
        // 마지막 명령을 받은 후 cmdTimeout 이상 지나면 true

        float linearCmd = timedOut ? 0f : cmdLinear;
        // timeout이면 선속도 명령 0 처리

        float angularCmd = timedOut ? 0f : cmdAngular;
        // timeout이면 회전 명령 0 처리

        ApplyDrive(linearCmd, angularCmd, timedOut);
        // 실제 조향, 토크, 브레이크 계산 및 적용

        if (clampRigidBodySpeed && rb.linearVelocity.magnitude > maxBodySpeed)
        {
            rb.linearVelocity = rb.linearVelocity.normalized * maxBodySpeed;
            // 실제 물리 속도가 한계 이상이면 강제로 제한
        }

        UpdateWheelVisuals();
        // WheelCollider 위치/회전을 시각 모델에 동기화

        if ((printDebug || printGroundedDebug) && Time.time - lastDebugTime > debugInterval)
        {
            lastDebugTime = Time.time;
            PrintWheelDebug();
            // 일정 주기로 접지 상태, RPM, 속도 로그 출력
        }
    }

    void CmdVelCallback(TwistMsg msg)
    {
        cmdLinear = Mathf.Clamp((float)msg.linear.x, -inputLinearLimit, inputLinearLimit);
        // ROS linear.x를 받아 허용 범위로 제한 후 저장

        cmdAngular = Mathf.Clamp((float)msg.angular.z, -inputAngularLimit, inputAngularLimit);
        // ROS angular.z를 받아 허용 범위로 제한 후 저장

        lastCmdTime = Time.time;
        // 마지막 명령 수신 시각 갱신
    }

    void SetupWheelCollider(WheelCollider wc)
    {
        if (wc == null) return;

        wc.radius = wheelRadius;
        // 바퀴 반지름 설정

        wc.mass = wheelMass;
        // 바퀴 질량 설정

        wc.wheelDampingRate = wheelDampingRate;
        // 바퀴 회전 감쇠 설정

        wc.suspensionDistance = suspensionDistance;
        // 서스펜션 이동 거리 설정

        wc.forceAppPointDistance = forceAppPointDistance;
        // 힘 작용점 설정

        JointSpring js = wc.suspensionSpring;
        js.spring = suspensionSpring;
        // 서스펜션 스프링 강성

        js.damper = suspensionDamper;
        // 서스펜션 감쇠

        js.targetPosition = suspensionTargetPosition;
        // 서스펜션 기본 위치

        wc.suspensionSpring = js;

        WheelFrictionCurve fwd = wc.forwardFriction;
        fwd.extremumSlip = 0.4f;
        fwd.extremumValue = 1f;
        fwd.asymptoteSlip = 0.8f;
        fwd.asymptoteValue = 0.75f;
        fwd.stiffness = forwardStiffness;
        wc.forwardFriction = fwd;
        // 전후 방향 마찰 곡선 설정
        // stiffness가 핵심 체감 파라미터

        WheelFrictionCurve side = wc.sidewaysFriction;
        side.extremumSlip = 0.2f;
        side.extremumValue = 1f;
        side.asymptoteSlip = 0.5f;
        side.asymptoteValue = 0.85f;
        side.stiffness = sidewaysStiffness;
        wc.sidewaysFriction = side;
        // 좌우 방향 마찰 곡선 설정
        // 코너링 안정성과 횡미끄럼에 크게 영향

        wc.motorTorque = 0f;
        wc.brakeTorque = 0f;
        wc.steerAngle = 0f;
        // 시작 시 토크, 브레이크, 조향각 초기화
    }

    void ApplyDrive(float linearCmd, float angularCmd, bool timedOut)
    {
        float signedForwardSpeed = GetForwardSpeed();
        // 차량 전방 기준 실제 속도(+면 전진, -면 후진)

        float currentSpeedAbs = Mathf.Abs(signedForwardSpeed);
        // 실제 전후 속도의 절대값

        float uphillAngle = GetUphillAngle(linearCmd);
        // 현재 진행 방향 기준 오르막 각도 계산

        float uphillAssist = enableHillAssist
            ? Mathf.InverseLerp(0f, Mathf.Max(1f, uphillAngleForFullAssist), uphillAngle)
            : 0f;
        // 0~1 범위의 언덕 보조 강도
        // 0이면 평지, 1이면 충분히 가파른 언덕

        // 속도에 따른 조향 제한
        float speedRatio = Mathf.Clamp01(currentSpeedAbs / Mathf.Max(inputLinearLimit, 0.01f));
        float dynamicMaxSteer = Mathf.Lerp(maxSteerAngleLowSpeed, maxSteerAngleHighSpeed, speedRatio);
        // 속도 비율에 따라 허용 최대 조향각을 보간
        // 현재 low/high 값이 같아서 사실상 고정 조향각처럼 동작

        float absAngular = Mathf.Abs(angularCmd);
        bool pivotMode = absAngular > pivotBoostAngular;
        // 회전 명령이 큰 경우 pivot 모드로 판단

        if (pivotMode)
            dynamicMaxSteer *= pivotSteerMultiplier;
        // pivot 모드면 최대 조향각 확대

        // cmd_vel -> steer angle
        float targetSteer = 0f;
        if (Mathf.Abs(linearCmd) > 0.02f || absAngular > 0.08f)
        {
            float steerDenom = Mathf.Max(Mathf.Abs(linearCmd), 0.12f);
            // 속도가 너무 작을 때 0으로 나누는 문제 방지용 최소 분모

            targetSteer = Mathf.Rad2Deg * Mathf.Atan((wheelBase * angularCmd) / steerDenom);
            // 자전거 모델 기반으로 linear/angular를 조향각으로 변환

            targetSteer = Mathf.Clamp(targetSteer, -dynamicMaxSteer, dynamicMaxSteer);
            // 허용 최대 조향각 내로 제한
        }

        if (invertSteering)
            targetSteer = -targetSteer;
        // 조향 반전 옵션 적용

        currentSteerAngle = Mathf.Lerp(
            currentSteerAngle,
            targetSteer,
            Time.fixedDeltaTime * steerResponse
        );
        // 실제 조향각을 목표 조향각으로 부드럽게 이동

        if (wcFL != null) wcFL.steerAngle = currentSteerAngle;
        if (wcFR != null) wcFR.steerAngle = currentSteerAngle;
        // 앞바퀴 두 개에 동일한 조향각 적용

        // 회전 시 선속도 감속
        float effectiveLinearCmd = linearCmd;
        if (slowDownWhenTurning)
        {
            float t = Mathf.InverseLerp(turnSlowdownStart, inputAngularLimit, absAngular);
            // 회전 크기가 감속 시작점에서 최대 입력까지 어느 정도인지 0~1로 환산

            float turnMinFactor = Mathf.Lerp(
                turnSlowdownMinFactor,
                uphillTurnSlowdownMinFactor,
                uphillAssist
            );
            // 언덕일수록 감속을 덜 하도록 최소 감속 배율 보간

            float slowdown = Mathf.Lerp(1f, turnMinFactor, t);
            // 회전이 클수록 1 -> turnMinFactor로 감속 적용

            effectiveLinearCmd *= slowdown;
            // 감속된 선속도 명령
        }

        // pivot 중 linear 너무 과하면 제한
        if (pivotMode)
        {
            effectiveLinearCmd = Mathf.Clamp(effectiveLinearCmd, -pivotSpeedLimit, pivotSpeedLimit);
        }
        // 회전이 큰 상태에서 전진/후진 속도를 별도 상한으로 제한

        // 구동 방향 반전
        float driveInput = invertDrive ? -effectiveLinearCmd : effectiveLinearCmd;
        // 차량 모델 전방축과 ROS 전방 정의가 반대일 때 보정

        // 낮은 속도에서 출발 보정
        float launchAssist = Mathf.Lerp(launchTorqueBoost, 1f, Mathf.Clamp01(currentSpeedAbs / 2.0f));
        // 정지 근처에서는 토크를 더 주고, 속도가 오르면 출발 보정을 점차 제거

        // 속도 올라갈수록 토크 자연 감쇠
        float torqueFade = 1f - Mathf.Clamp01(currentSpeedAbs / Mathf.Max(inputLinearLimit, 0.01f));
        // 실제 속도가 inputLinearLimit에 가까워질수록 토크를 줄이기 위한 계수 계산

        float speedTorqueFactor = Mathf.Lerp(0.45f, 1.0f, torqueFade);
        // 고속에서는 토크를 다소 줄이고 저속에서는 더 충분히 주기 위한 배율

        float motorTorque = driveInput * maxMotorTorque * launchAssist * speedTorqueFactor;
        // 기본 최종 구동 토크 계산

        motorTorque *= Mathf.Lerp(1f, uphillTorqueMultiplier, uphillAssist);
        // 언덕이면 토크 추가 증폭

        if (Mathf.Abs(effectiveLinearCmd) < 0.02f)
            motorTorque = 0f;
        // 사실상 정지 명령이면 구동 토크 제거

        // 언덕에서 auto AWD
        bool useFrontDriveNow = frontWheelDrive;
        bool useRearDriveNow = rearWheelDrive;
        // 기본 구동축 설정 가져오기

        if (enableAutoAWDOnHill && uphillAssist > 0.05f)
        {
            useFrontDriveNow = true;
            useRearDriveNow = true;
        }
        // 언덕이면 자동으로 앞뒤 모두 구동

        float frontDriveBias = 0f;
        float rearDriveBias = 0f;
        // 앞축/뒤축 토크 분배 비율

        if (useFrontDriveNow && useRearDriveNow)
        {
            frontDriveBias = Mathf.Lerp(0.5f, uphillFrontDriveBias, uphillAssist);
            rearDriveBias = 1f - frontDriveBias;
            // AWD일 때 앞뒤 토크 비율 결정
        }
        else if (useFrontDriveNow)
        {
            frontDriveBias = 1f;
            rearDriveBias = 0f;
            // 전륜만 구동
        }
        else if (useRearDriveNow)
        {
            frontDriveBias = 0f;
            rearDriveBias = 1f;
            // 후륜만 구동
        }

        // 접지 부족 시 전달 토크 줄이기보다, 접지된 축 위주로 유지
        int frontGrounded = CountGrounded(wcFL, wcFR);
        int rearGrounded = CountGrounded(wcRL, wcRR);
        // 앞축/뒤축에서 몇 개 바퀴가 땅에 닿아 있는지 계산

        float frontTorque = 0f;
        float rearTorque = 0f;

        if (frontGrounded > 0) frontTorque = motorTorque * frontDriveBias;
        if (rearGrounded > 0) rearTorque = motorTorque * rearDriveBias;
        // 접지된 축에만 토크 전달

        // 둘 다 접지 안 되어 있으면 그냥 원래 배분
        if (frontGrounded == 0 && rearGrounded == 0)
        {
            frontTorque = motorTorque * frontDriveBias;
            rearTorque = motorTorque * rearDriveBias;
        }
        // 공중에 뜬 극단적 경우엔 원래 배분 유지

        ApplyMotorTorque(frontTorque, rearTorque);
        // 앞축/뒤축 토크를 실제 각 바퀴에 적용

        // 브레이크
        float appliedBrake = 0f;

        if (timedOut)
        {
            appliedBrake = holdBrakeTorque;
            // 명령 끊김 시 강하게 정지 유지
        }
        else if (Mathf.Abs(linearCmd) < 0.02f && absAngular < 0.05f)
        {
            appliedBrake = brakeTorque;
            // 선속도/회전 명령 모두 거의 없으면 일반 정지 브레이크
        }
        else if (Mathf.Abs(linearCmd) < 0.02f && absAngular >= 0.05f)
        {
            appliedBrake = idleBrakeTorque;
            // 제자리 조향하려는 상황에서는 브레이크를 약하게만 걸어 핸들 회전성 확보
        }
        else
        {
            appliedBrake = 0f;
            // 주행 중에는 브레이크 해제
        }

        ApplyBrakeTorque(appliedBrake);
        // 네 바퀴에 동일한 브레이크 적용
    }

    void ApplyMotorTorque(float frontTorque, float rearTorque)
    {
        if (wcFL != null) wcFL.motorTorque = frontTorque * 0.5f;
        if (wcFR != null) wcFR.motorTorque = frontTorque * 0.5f;
        // 앞축 토크를 앞바퀴 두 개에 반씩 분배

        if (wcRL != null) wcRL.motorTorque = rearTorque * 0.5f;
        if (wcRR != null) wcRR.motorTorque = rearTorque * 0.5f;
        // 뒤축 토크를 뒤바퀴 두 개에 반씩 분배
    }

    void ApplyBrakeTorque(float brake)
    {
        if (wcFL != null) wcFL.brakeTorque = brake;
        if (wcFR != null) wcFR.brakeTorque = brake;
        if (wcRL != null) wcRL.brakeTorque = brake;
        if (wcRR != null) wcRR.brakeTorque = brake;
        // 네 바퀴 모두 같은 브레이크 토크 적용
    }

    void UpdateWheelVisuals()
    {
        SyncWheel(wcFL, visFL, visFLOffset);
        SyncWheel(wcFR, visFR, visFROffset);
        SyncWheel(wcRL, visRL, visRLOffset);
        SyncWheel(wcRR, visRR, visRROffset);
        // 실제 WheelCollider 상태를 시각용 바퀴 모델에 반영
    }

    void SyncWheel(WheelCollider wc, Transform vis, Vector3 offsetEuler)
    {
        if (wc == null || vis == null) return;

        Vector3 pos;
        Quaternion rot;
        wc.GetWorldPose(out pos, out rot);
        // WheelCollider의 월드 위치/회전 읽기

        vis.position = pos;
        // 시각 모델 위치 맞춤

        vis.rotation = rot * Quaternion.Euler(offsetEuler);
        // 시각 모델 회전에 보정 오프셋을 곱해서 방향 맞춤
    }

    float GetForwardSpeed()
    {
        return Vector3.Dot(rb.linearVelocity, GetVehicleForward());
        // Rigidbody 실제 속도를 차량 전방 방향에 투영
        // 전진이면 +, 후진이면 - 값
    }

    Vector3 GetVehicleForward()
    {
        return invertDrive ? -transform.forward : transform.forward;
        // 차량이 실제로 어느 축을 전방으로 볼지 결정
    }

    float GetUphillAngle(float linearCmd)
    {
        if (!enableHillAssist || Mathf.Abs(linearCmd) < 0.02f)
            return 0f;
        // 언덕 보조가 꺼져 있거나 사실상 정지 명령이면 0도 처리

        Vector3 driveForward = GetVehicleForward();
        // 현재 차량 기준 전방 벡터

        if (linearCmd < 0f)
            driveForward = -driveForward;
        // 후진 중이면 반대 방향을 진행 방향으로 간주

        float uphillComponent = Mathf.Clamp(driveForward.y, -1f, 1f);
        // 진행 방향 벡터의 y 성분만 뽑아 경사 정도 추정

        return Mathf.Max(0f, Mathf.Asin(uphillComponent) * Mathf.Rad2Deg);
        // 오르막 각도만 반환, 내리막은 0 처리
    }

    int CountGrounded(WheelCollider a, WheelCollider b)
    {
        int c = 0;
        if (a != null && a.isGrounded) c++;
        if (b != null && b.isGrounded) c++;
        return c;
        // 두 바퀴 중 지면에 닿아 있는 바퀴 수 반환
    }

    void PrintWheelDebug()
    {
        bool gFL = wcFL != null && wcFL.isGrounded;
        bool gFR = wcFR != null && wcFR.isGrounded;
        bool gRL = wcRL != null && wcRL.isGrounded;
        bool gRR = wcRR != null && wcRR.isGrounded;
        // 각 바퀴 접지 여부

        float rpmFL = wcFL != null ? wcFL.rpm : 0f;
        float rpmFR = wcFR != null ? wcFR.rpm : 0f;
        float rpmRL = wcRL != null ? wcRL.rpm : 0f;
        float rpmRR = wcRR != null ? wcRR.rpm : 0f;
        // 각 바퀴 RPM

        if (printGroundedDebug || printDebug)
        {
            Debug.Log(
                $"Grounded FL:{gFL} FR:{gFR} RL:{gRL} RR:{gRR} | " +
                $"RPM FL:{rpmFL:F1} FR:{rpmFR:F1} RL:{rpmRL:F1} RR:{rpmRR:F1} | " +
                $"Vel:{rb.linearVelocity.magnitude:F2} | ForwardSpeed:{GetForwardSpeed():F2}"
            );
            // 접지 상태, 바퀴 회전수, 전체 속도, 전방 기준 속도 출력
        }
    }
}