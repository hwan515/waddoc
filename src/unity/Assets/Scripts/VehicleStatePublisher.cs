using UnityEngine;
using Unity.Robotics.ROSTCPConnector;


using RosMessageTypes.Std;
using RosMessageTypes.Nav;
using RosMessageTypes.Geometry;
using RosMessageTypes.Tf2;
using RosMessageTypes.BuiltinInterfaces;

public class VehicleStatePublisher : MonoBehaviour
{
    [Header("ROS Topics")]
    public string odomTopic = "/odom";
    public string tfTopic = "/tf";
    public string wheelStateTopic = "/wheel_states";

    [Header("Frame IDs")]
    public string odomFrame = "odom";
    public string baseFrame = "base_link";

    [Header("Publish Rate")]
    public int publishHz = 20;

    [Header("References")]
    public Rigidbody rb;
    public WheelCollider wcFL;
    public WheelCollider wcFR;
    public WheelCollider wcRL;
    public WheelCollider wcRR;

    private ROSConnection ros;
    private float publishInterval;
    private float lastPublishTime = -999f;

    void Start()
    {
        ros = ROSConnection.GetOrCreateInstance();

        ros.RegisterPublisher<OdometryMsg>(odomTopic);
        ros.RegisterPublisher<TFMessageMsg>(tfTopic);
        ros.RegisterPublisher<Float32MultiArrayMsg>(wheelStateTopic);

        if (rb == null)
            rb = GetComponent<Rigidbody>();

        publishInterval = 1.0f / Mathf.Max(1, publishHz);
    }

    void FixedUpdate()
    {
        if (Time.time - lastPublishTime < publishInterval)
            return;

        PublishAll();
        lastPublishTime = Time.time;
    }

    void PublishAll()
    {
        double now = Time.timeAsDouble;
        int sec = (int)now;
        uint nanosec = (uint)((now - sec) * 1e9);

        TimeMsg stamp = new TimeMsg(sec, nanosec);

        PublishOdom(stamp);
        PublishTF(stamp);
        PublishWheelStates();
    }

    void PublishOdom(TimeMsg stamp)
    {
        Vector3 unityPos = rb != null ? rb.position : transform.position;
        Quaternion unityRot = rb != null ? rb.rotation : transform.rotation;

        Vector3 unityVelWorld = rb != null ? rb.linearVelocity : Vector3.zero;
        Vector3 unityAngVelWorld = rb != null ? rb.angularVelocity : Vector3.zero;

        Transform refTf = rb != null ? rb.transform : transform;

        Vector3 unityVelLocal = refTf.InverseTransformDirection(unityVelWorld);
        Vector3 unityAngVelLocal = refTf.InverseTransformDirection(unityAngVelWorld);

        unityVelLocal.z *= -1.0f;
        unityAngVelLocal.z *= -1.0f; 

        Vector3 rosPos = UnityToRosPosition(unityPos);
        Quaternion rosQuat = UnityToRosQuaternion(unityRot);

        Vector3 rosVel = UnityToRosVector(unityVelLocal);
        Vector3 rosAngVel = UnityToRosVector(unityAngVelLocal);

        HeaderMsg header = new HeaderMsg();
        header.frame_id = odomFrame;
        header.stamp = stamp;

        PoseMsg pose = new PoseMsg(
            new PointMsg(rosPos.x, rosPos.y, rosPos.z),
            new QuaternionMsg(rosQuat.x, rosQuat.y, rosQuat.z, rosQuat.w)
        );

        TwistMsg twist = new TwistMsg(
            new Vector3Msg(rosVel.x, rosVel.y, rosVel.z),
            new Vector3Msg(rosAngVel.x, rosAngVel.y, rosAngVel.z)
        );

        PoseWithCovarianceMsg poseCov = new PoseWithCovarianceMsg();
        poseCov.pose = pose;

        TwistWithCovarianceMsg twistCov = new TwistWithCovarianceMsg();
        twistCov.twist = twist;

        OdometryMsg odom = new OdometryMsg();
        odom.header = header;
        odom.child_frame_id = baseFrame;
        odom.pose = poseCov;
        odom.twist = twistCov;

        ros.Publish(odomTopic, odom);
    }

    void PublishTF(TimeMsg stamp)
    {
        Vector3 unityPos = rb != null ? rb.position : transform.position;
        Quaternion unityRot = rb != null ? rb.rotation : transform.rotation;

        Vector3 rosPos = UnityToRosPosition(unityPos);
        Quaternion rosQuat = UnityToRosQuaternion(unityRot);

        HeaderMsg header = new HeaderMsg();
        header.frame_id = odomFrame;
        header.stamp = stamp;

        TransformStampedMsg tfStamped = new TransformStampedMsg();
        tfStamped.header = header;
        tfStamped.child_frame_id = baseFrame;
        tfStamped.transform = new TransformMsg(
            new Vector3Msg(rosPos.x, rosPos.y, rosPos.z),
            new QuaternionMsg(rosQuat.x, rosQuat.y, rosQuat.z, rosQuat.w)
        );

        TFMessageMsg tfMsg = new TFMessageMsg(new TransformStampedMsg[] { tfStamped });
        ros.Publish(tfTopic, tfMsg);
    }

    void PublishWheelStates()
    {
        float flRpm = wcFL != null ? wcFL.rpm : 0f;
        float frRpm = wcFR != null ? wcFR.rpm : 0f;
        float rlRpm = wcRL != null ? wcRL.rpm : 0f;
        float rrRpm = wcRR != null ? wcRR.rpm : 0f;

        float flSteer = wcFL != null ? wcFL.steerAngle : 0f;
        float frSteer = wcFR != null ? wcFR.steerAngle : 0f;

        Float32MultiArrayMsg wheelMsg = new Float32MultiArrayMsg(
            new MultiArrayLayoutMsg(),
            new float[] { flRpm, frRpm, rlRpm, rrRpm, flSteer, frSteer }
        );

        ros.Publish(wheelStateTopic, wheelMsg);
    }

    Vector3 UnityToRosPosition(Vector3 unity)
    {
        return new Vector3(unity.z, -unity.x, unity.y);
    }

    Vector3 UnityToRosVector(Vector3 unity)
    {
        return new Vector3(unity.z, -unity.x, unity.y);
    }

    Quaternion UnityToRosQuaternion(Quaternion q)
    {
        return new Quaternion(q.z, -q.x, q.y, q.w);
    }
}