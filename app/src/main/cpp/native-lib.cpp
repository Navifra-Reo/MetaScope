#include <jni.h>
#include "vo_features.h"
#include <android/log.h>

#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <stdlib.h>
#include <string.h>

#define BUFSIZE 255
#define PORT 23
#define SERVER_DOMAIN "10.0.2.2"

#define MIN_NUM_FEAT 1500

int sock;
char buf[BUFSIZE];

struct sockaddr_in svr_addr;
struct hostent *pHostEnt;

//카메라 변수
Mat camMatrix;

//이미지
Mat prevImage, currImage;
//로테이션, 이동 관련 변수
Mat R_f, t_f;
Mat E, R, t, mask;

bool isinit=false;

//특징점
vector<Point2f> prevFeatures;
vector<Point2f> currFeatures;

//다음프레임으로 가기 전 처리
void setFrame()
{
    prevImage = currImage.clone();
    prevFeatures = currFeatures;
}

std::string jstring2string(JNIEnv *env, jstring jStr) {
    if (!jStr)
        return "";

    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes, env->NewStringUTF("UTF-8"));

    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string ret = std::string((char *)pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

//camMatrix 체크
void getCameraMatrix_() {
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix_", "camMatrixSize : %d %d", camMatrix.size().width, camMatrix.size().height);
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix_", "camMatrix : ");
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix_", "%f %f %f", camMatrix.at<double>(0, 0), camMatrix.at<double>(0, 1), camMatrix.at<double>(0, 2));
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix_", "%f %f %f", camMatrix.at<double>(1, 0), camMatrix.at<double>(1, 1), camMatrix.at<double>(1, 2));
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix_", "%f %f %f", camMatrix.at<double>(2, 0), camMatrix.at<double>(2, 1), camMatrix.at<double>(2, 2));
}

//camMatrix MainActivity에서 체크
extern "C"
JNIEXPORT void JNICALL
Java_com_zlegamer_metascope_MainActivity_getCameraMatrix(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix", "camMatrixSize : %d %d", camMatrix.size().width, camMatrix.size().height);
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix", "camMatrix : ");
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix", "%f %f %f", camMatrix.at<double>(0, 0), camMatrix.at<double>(0, 1), camMatrix.at<double>(0, 2));
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix", "%f %f %f", camMatrix.at<double>(1, 0), camMatrix.at<double>(1, 1), camMatrix.at<double>(1, 2));
    __android_log_print(ANDROID_LOG_INFO, "getCameraMatrix", "%f %f %f", camMatrix.at<double>(2, 0), camMatrix.at<double>(2, 1), camMatrix.at<double>(2, 2));
}

//pair 처리용
jdouble extract_double(JNIEnv *env, jobject f) {
    // Note the syntax of signatures: double doubleValue() has signature "()F"
    return env->CallDoubleMethod(f, env->GetMethodID(env->FindClass("java/lang/Double"), "doubleValue", "()D"));
}
pair<double, double> extract_pair(JNIEnv *env, jobject p) {
    jclass pairClass = env->GetObjectClass(p);
    jfieldID first = env->GetFieldID(pairClass, "first", "Ljava/lang/Object;");
    jfieldID second = env->GetFieldID(pairClass, "second", "Ljava/lang/Object;");
    double fir = extract_double(env, env->GetObjectField(p,first));
    double sec = extract_double(env, env->GetObjectField(p,second));
    return make_pair(fir, sec);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_zlegamer_metascope_MainActivity_initVo(JNIEnv *env, jobject thiz, jobject cam_matrix, jstring room, jstring name) {

    isinit=true;
    pair<double,double> focal = extract_pair(env, cam_matrix);
    __android_log_print(ANDROID_LOG_INFO, "JNI", "fx = %f", focal.first);
    __android_log_print(ANDROID_LOG_INFO, "JNI", "fy = %f", focal.second);
    //double data[]= { focal.first, 0, 300.0, 0, focal.second, 400.0, 0 ,0 ,1 };

    //__android_log_print(ANDROID_LOG_INFO, "vo", "data matrix : ");
    //__android_log_print(ANDROID_LOG_INFO, "vo", "%f %f %f", data[0], data[1], data[2]);
    //__android_log_print(ANDROID_LOG_INFO, "vo", "%f %f %f", data[3], data[4], data[5]);
    //__android_log_print(ANDROID_LOG_INFO, "vo", "%f %f %f", data[6], data[7], data[8]);

    //camMatrix = Mat(3, 3, CV_64FC1, data);
    R= Mat::zeros(3,3,CV_64FC1);
    t= Mat::zeros(3,1,CV_64FC1);

    //getCameraMatrix_();

    // 소켓 열기
    sock = socket(PF_INET, SOCK_STREAM, 0);

    __android_log_print(ANDROID_LOG_INFO,"socket test", "sock = %d", sock);
    memset(&svr_addr,0, sizeof(struct sockaddr_in));
    svr_addr.sin_family = AF_INET;
    svr_addr.sin_port = htons(PORT);
    pHostEnt = gethostbyname(SERVER_DOMAIN);
    svr_addr.sin_addr.s_addr = inet_addr(inet_ntoa(*((struct in_addr *)pHostEnt->h_addr)));
    connect(sock, (struct sockaddr*)&svr_addr, sizeof(struct sockaddr_in));

    string text = "E" + jstring2string(env, room) + "," + jstring2string(env, name);
    strcpy(buf,text.c_str());
    send(sock,buf,sizeof(buf),0);
}

//vo처리 관련
extern "C"
JNIEXPORT jstring JNICALL
Java_com_zlegamer_metascope_MainActivity_vo_1tracker(JNIEnv *env, jobject thiz, jlong mat_addr_input, jobject focallength, jboolean victim) {

    string text="X = 0\nY = 0\nZ = 0";
    if(false/**/)
    {
        recv(sock,buf,BUFSIZE,0);
        if(buf[1]!='T')return (*env).NewStringUTF(buf);
    }

    if(victim)
    {
        //맞췄을때
        double theta = asin(R_f.at<double>(3,1));
        double phi = atan2(R_f.at<double>(3,2),R_f.at<double>(3,3));

        text = "S"+to_string(phi)+","+ to_string(theta);
        strcpy(buf,text.c_str());
        send(sock, buf, sizeof(buf), 0);

        recv(sock,buf,BUFSIZE,0);
    }

    //camMatrix 생성
    pair<double,double> focal = extract_pair(env, focallength);
    double data[]= { focal.first, 0, 300.0, 0, focal.second, 400.0, 0, 0, 1 };
    camMatrix = Mat(3, 3, CV_64FC1, data);

    Mat &inputImage = *((Mat *)mat_addr_input);

    //이미지 입력 및 전처리(흑백으로 변경)
    cvtColor(inputImage, currImage, COLOR_BGR2GRAY);

    //올바르지 않은 데이터면 프래임이면 스킵
    if(!currImage.isContinuous()) return(*env).NewStringUTF(text.c_str());

    //첫번째 프레임이면 스킵
    if(isinit)
    {
        isinit = false;
        __android_log_print(ANDROID_LOG_INFO, "vo", "최초 이미지 사이즈 셋팅");

        featureDetection(currImage, currFeatures);

        setFrame();
        return(*env).NewStringUTF(text.c_str());
    }

    //이전 이미지에서 특징점 추출
    if(prevFeatures.size() < MIN_NUM_FEAT) featureDetection(prevImage, prevFeatures);

    __android_log_print(ANDROID_LOG_INFO, "vo", "prevFeature = %d", prevFeatures.size());

    // 이전 프래임의 특징점 갯수가 적으면
    if(prevFeatures.size()<5) { setFrame();
        __android_log_print(ANDROID_LOG_INFO, "vo", "Returned : prevFeatures.size < 5 and %d", prevFeatures.size());
        return(*env).NewStringUTF(text.c_str());
    }

    vector<uchar> status;
    featureTracking(prevImage, currImage, prevFeatures, currFeatures, status);
    __android_log_print(ANDROID_LOG_INFO, "vo", "currFeature = %d", currFeatures.size());

    //일치하는 특징점이 없는경우 리턴
    if(currFeatures.size()<5) { setFrame();
        __android_log_print(ANDROID_LOG_INFO, "vo", "Returned : currFeatures.size < 5 and %d", currFeatures.size());
        return(*env).NewStringUTF(text.c_str());
    }

    E = findEssentialMat(currFeatures, prevFeatures, camMatrix, RANSAC, 0.999, 1.0, mask);

    __android_log_print(ANDROID_LOG_INFO, "Essential Matrix", "E : ");
    __android_log_print(ANDROID_LOG_INFO, "Essential Matrix", "%f %f %f", E.at<double>(0, 0), E.at<double>(0, 1), E.at<double>(0, 2));
    __android_log_print(ANDROID_LOG_INFO, "Essential Matrix", "%f %f %f", E.at<double>(1, 0), E.at<double>(1, 1), E.at<double>(1, 2));
    __android_log_print(ANDROID_LOG_INFO, "Essential Matrix", "%f %f %f", E.at<double>(2, 0), E.at<double>(2, 1), E.at<double>(2, 2));

    //getCameraMatrix_();

    if(E.cols != 3 || E.rows != 3) { setFrame();
        return(*env).NewStringUTF(text.c_str());
    }
    recoverPose(E, currFeatures, prevFeatures, camMatrix, R, t, mask);

    // 현재 프레임이 시작점이면 해당값으로 초기화
    if(t_f.cols==0)
    {
        t_f = t.clone();
        R_f = R.clone();
        __android_log_print(ANDROID_LOG_INFO, "vo","최초 위치 갱신");
    }

    // 현재 프레임이 시작점이 아니면 이전 프레임 위치에서 갱신
    else
    {
        t_f = t_f + (R_f*t);
        R_f = R*R_f;
    }
    text = "P" + to_string(t_f.at<double>(0)) + "," + to_string(t_f.at<double>(1)) + "," + to_string(t_f.at<double>(2));

    setFrame();

    strcpy(buf,text.c_str());
    send(sock, buf, sizeof(buf), 0);

    return(*env).NewStringUTF(text.c_str());
}