#include <jni.h>
#include "vo_features.h"
#include <android/log.h>

#define MIN_NUM_FEAT 2000
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

//pair 처리용
jfloat extract_float(JNIEnv *env, jobject f) {
    // Note the syntax of signatures: float floatValue() has signature "()F"
    return env->CallFloatMethod(f, env->GetMethodID(env->FindClass("java/lang/Float"), "floatValue", "()F"));
}
pair<float, float> extract_pair(JNIEnv *env, jobject p) {
    jclass pairClass = env->GetObjectClass(p);
    jfieldID first = env->GetFieldID(pairClass, "first", "Ljava/lang/Object;");
    jfieldID second = env->GetFieldID(pairClass, "second", "Ljava/lang/Object;");
    float fir = extract_float(env, env->GetObjectField(p,first));
    float sec = extract_float(env, env->GetObjectField(p,second));
    return make_pair(fir, sec);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_zlegamer_metascope_MainActivity_initVo(JNIEnv *env, jobject thiz, jobject cam_matrix) {

    isinit=true;
    pair<float,float> focal = extract_pair(env, cam_matrix);
    __android_log_print(ANDROID_LOG_INFO, "JNI", "fx = %f", focal.first);
    __android_log_print(ANDROID_LOG_INFO, "JNI", "fy = %f", focal.second);
    float data[]= { focal.first, 0, 400.0, 0, focal.second, 300.0, 0 ,0 ,1 };
    camMatrix = Mat(3, 3, CV_32FC1, data);
    R_f= Mat::zeros(3,3,CV_64FC1);
    t_f= Mat::zeros(3,1,CV_64FC1);
}

//vo처리 관련
extern "C"
JNIEXPORT void JNICALL
Java_com_zlegamer_metascope_MainActivity_vo_1tracker(JNIEnv *env, jobject thiz, jlong mat_addr_input) {

    Mat &inputImage = *(Mat *) mat_addr_input;

    //이미지 입력 및 전처리(흑백으로 변경)
    cvtColor(inputImage, currImage, COLOR_BGR2GRAY);

    //올바르지 않은 데이터면 프래임이면 스킵
    if(!currImage.isContinuous())return;

    //첫번째 프레임이면 스킵
    if(isinit)
    {
        isinit=false;
        __android_log_print(ANDROID_LOG_INFO, "vo", "최초 이미지 사이즈 셋팅");

        featureDetection(currImage, currFeatures);

        setFrame();
        return;
    }

    //이전 이미지에서 특징점 추출
    if(prevFeatures.size() < MIN_NUM_FEAT) featureDetection(prevImage, prevFeatures);

    __android_log_print(ANDROID_LOG_INFO, "vo", "prevFeature = %d", prevFeatures.size());

    // 이전 프래임의 특징점 갯수가 적으면
    if(prevFeatures.size()<5) { setFrame(); return; }

    vector<uchar> status;
    featureTracking(prevImage, currImage, prevFeatures, currFeatures, status);
    __android_log_print(ANDROID_LOG_INFO, "vo", "currFeature = %d", currFeatures.size());

    //일치하는 특징점이 없는경우 리턴
    if(currFeatures.size()<5) { setFrame(); return; }

    E = findEssentialMat(currFeatures, prevFeatures, camMatrix, RANSAC, 0.999, 1.0, mask);

    if(E.cols != 3 || E.rows != 3) { setFrame(); return; }
    recoverPose(E, currFeatures, prevFeatures, camMatrix, R, t, mask);

    // 현재 프레임이 시작점이면 해당값으로 초기화
    if(t_f.cols==0)
    {
        t_f = t.clone();
        R_f = R.clone();
        __android_log_print(ANDROID_LOG_INFO, "vo","최초 위치 갱신");
    }

    // 현재 프레임이 시작점이 아니면 이전 프레임 위치에서 갱신
    else if (((t.at<double>(2) > t.at<double>(0)) && (t.at<double>(2) > t.at<double>(1))))
    {
        t_f = t_f + (R_f*t);
        R = R*R_f;
    }

    __android_log_print(ANDROID_LOG_INFO, "vo","x = %f",t_f.at<double>(0,0));
    __android_log_print(ANDROID_LOG_INFO, "vo","y = %f",t_f.at<double>(0,1));
    __android_log_print(ANDROID_LOG_INFO, "vo","z = %f",t_f.at<double>(0,2));

    setFrame();
}