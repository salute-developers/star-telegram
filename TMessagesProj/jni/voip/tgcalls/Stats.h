#ifndef TGCALLS_STATS_H
#define TGCALLS_STATS_H

namespace tgcalls {

enum class CallStatsConnectionEndpointType {
    ConnectionEndpointP2P = 0,
    ConnectionEndpointTURN = 1
};

struct CallStatsNetworkRecord {
    int32_t timestamp = 0;
    CallStatsConnectionEndpointType endpointType = CallStatsConnectionEndpointType::ConnectionEndpointP2P;
    bool isLowCost = false;
};

struct CallStatsBitrateRecord {
    int32_t timestamp = 0;
    int32_t bitrate = 0;
};

struct VideoStats {
    int input_frame_rate = 0;
    int encode_frame_rate = 0;
    int avg_encode_time_ms = 0;

    uint32_t frames_encoded = 0;
    uint32_t frames_dropped_by_capturer = 0;
    uint32_t frames_dropped_by_encoder_queue = 0;
    uint32_t frames_dropped_by_rate_limiter = 0;
    uint32_t frames_dropped_by_congestion_window = 0;
    uint32_t frames_dropped_by_encoder = 0;
};

struct CallStats {
    std::string outgoingCodec;
    std::vector<CallStatsNetworkRecord> networkRecords;
    std::vector<CallStatsBitrateRecord> bitrateRecords;

    VideoStats videoStats;
};

} // namespace tgcalls

#endif
