using System;
using UnityEngine;
using Unity.Robotics.ROSTCPConnector;

[DefaultExecutionOrder(-1000)]
public class RosTcpEndpointBootstrap : MonoBehaviour
{
    [Header("Optional Overrides")]
    [SerializeField] string endpointIpOverride = "";
    [SerializeField] int endpointPortOverride = 0;
    [SerializeField] bool syncPlayerPrefs = true;
    [SerializeField] bool logResolvedEndpoint = true;

    const string RosIpArgPrefix = "--ros-ip=";
    const string RosPortArgPrefix = "--ros-port=";
    const string RosTcpEndpointIpEnv = "ROS_TCP_ENDPOINT_IP";
    const string RosTcpEndpointPortEnv = "ROS_TCP_ENDPOINT_PORT";
    const string RosIpEnv = "ROS_IP";

    void Awake()
    {
        ROSConnection ros = GetComponent<ROSConnection>();
        if (ros == null)
        {
            Debug.LogWarning("[RosTcpEndpointBootstrap] ROSConnection component not found.");
            return;
        }

        string resolvedIp = ResolveEndpointIp(ros.RosIPAddress);
        int resolvedPort = ResolveEndpointPort(ros.RosPort);

        ros.RosIPAddress = resolvedIp;
        ros.RosPort = resolvedPort;

        if (syncPlayerPrefs)
        {
            ROSConnection.SetIPPref(resolvedIp);
            ROSConnection.SetPortPref(resolvedPort);
        }

        if (logResolvedEndpoint)
        {
            Debug.Log(
                $"[RosTcpEndpointBootstrap] ROS-TCP endpoint -> {resolvedIp}:{resolvedPort}"
            );
        }
    }

    string ResolveEndpointIp(string fallbackIp)
    {
        string commandLineIp = ResolveCommandLineValue(RosIpArgPrefix);
        string envIp = FirstNonEmpty(
            Environment.GetEnvironmentVariable(RosTcpEndpointIpEnv),
            Environment.GetEnvironmentVariable(RosIpEnv)
        );

        if (!string.IsNullOrWhiteSpace(endpointIpOverride))
            return endpointIpOverride.Trim();

        if (!string.IsNullOrWhiteSpace(commandLineIp))
            return commandLineIp.Trim();

        if (!string.IsNullOrWhiteSpace(envIp))
            return envIp.Trim();

        string playerPrefsIp = ROSConnection.RosIPAddressPref;
        if (!string.IsNullOrWhiteSpace(playerPrefsIp) && playerPrefsIp != fallbackIp)
            return playerPrefsIp.Trim();

        return fallbackIp;
    }

    int ResolveEndpointPort(int fallbackPort)
    {
        if (endpointPortOverride > 0)
            return endpointPortOverride;

        string commandLinePort = ResolveCommandLineValue(RosPortArgPrefix);
        if (TryParsePositiveInt(commandLinePort, out int parsedCommandLinePort))
            return parsedCommandLinePort;

        string envPort = Environment.GetEnvironmentVariable(RosTcpEndpointPortEnv);
        if (TryParsePositiveInt(envPort, out int parsedEnvPort))
            return parsedEnvPort;

        int playerPrefsPort = ROSConnection.RosPortPref;
        if (playerPrefsPort > 0 && playerPrefsPort != fallbackPort)
            return playerPrefsPort;

        return fallbackPort;
    }

    static string ResolveCommandLineValue(string prefix)
    {
        foreach (string arg in Environment.GetCommandLineArgs())
        {
            if (arg.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                return arg.Substring(prefix.Length);
        }

        return null;
    }

    static string FirstNonEmpty(params string[] values)
    {
        foreach (string value in values)
        {
            if (!string.IsNullOrWhiteSpace(value))
                return value;
        }

        return null;
    }

    static bool TryParsePositiveInt(string rawValue, out int parsedValue)
    {
        if (int.TryParse(rawValue, out parsedValue))
            return parsedValue > 0;

        parsedValue = 0;
        return false;
    }
}
