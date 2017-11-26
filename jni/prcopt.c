
#include <android/log.h>
#include <jni.h>
#include <strings.h>

#include "rtklib.h"
#include "rtkjni.h"

#define TAG "nativeProcessingOptions"
#define LOGV(...) showmsg(__VA_ARGS__)

#define PROCESSING_OPTIONS_CLASS "gpsplus/rtklib/ProcessingOptions$Native"
#define SNR_MASK_CLASS "gpsplus/rtklib/ProcessingOptions$SnrMask"

static struct {
   jfieldID mode;
   jfieldID soltype;
   jfieldID nf;
   jfieldID navsys;
   jfieldID elmin;
   jfieldID snrmask;
   jfieldID sateph;
   jfieldID modear;
   jfieldID glomodear;

// Advanced options for rtkexplorer
   jfieldID gpsmodear;
   jfieldID bdsmodear;
   jfieldID arfilter;
   jfieldID minfixsats;
   jfieldID minholdsats;
   jfieldID mindropsats;
   jfieldID rcvstds;
   jfieldID armaxiter;
   jfieldID varholdamb;
   jfieldID gainholdamb;
   jfieldID maxaveep;
   jfieldID initrst;
   jfieldID outsingle;
   jfieldID syncsol;
   jfieldID freqopt;

//

   jfieldID maxout;
   jfieldID minlock;
   jfieldID minfix;
   jfieldID ionoopt;
   jfieldID tropopt;
   jfieldID dynamics;
   jfieldID tidecorr;
   jfieldID niter;
   jfieldID codesmooth;
   jfieldID intpref;
   jfieldID sbascorr;
   jfieldID sbassatsel;
   jfieldID rovpos;
   jfieldID refpos;
   jfieldID eratioL1;
   jfieldID eratioL2;
   jfieldID eratioL5;
   jfieldID errPhaseA;
   jfieldID errPhaseB;
   jfieldID errPhaseC;
   jfieldID errDopplerFreq;
   jfieldID stdBias;
   jfieldID stdIono;
   jfieldID stdTrop;
   jfieldID prnBias;
   jfieldID prnIono;
   jfieldID prnTrop;
   jfieldID prnAcch;
   jfieldID prnAccv;
   jfieldID sclkstab;
   jfieldID thresar_0;
   jfieldID thresar_1;
   jfieldID thresar_2;
   jfieldID thresar_3;
   jfieldID elmaskar;
   jfieldID elmaskhold;
   jfieldID thresslip;
   jfieldID maxtdiff;
   jfieldID maxinno;
   jfieldID maxgdop;
   jfieldID baselineConst;
   jfieldID baselineSigma;
   jfieldID ruX;
   jfieldID ruY;
   jfieldID ruZ;
   jfieldID baseX;
   jfieldID baseY;
   jfieldID baseZ;
   jfieldID anttypeBase;
   jfieldID anttypeRover;
   jfieldID antdelRovE;
   jfieldID antdelRovN;
   jfieldID antdelRovU;
   jfieldID antdelRefE;
   jfieldID antdelRefN;
   jfieldID antdelRefU;
   jfieldID exsats;
   jfieldID rnxoptBase;
   jfieldID rnxoptRover;
   jfieldID posopt;
} gpsplus_rtklib_prcopt_fields;

static struct {
   jfieldID enableRover;
   jfieldID enableBase;
   jfieldID maskL1;
   jfieldID maskL2;
   jfieldID maskL5;
} gpsplus_rtklib_snrmask_fields;

static int init_prcopt_fields_methods(JNIEnv* env, jclass clazz);
static int init_snrmask_fields_methods(JNIEnv* env, jclass clazz);
jboolean SnrMask_load(JNIEnv* env, jobject j_snrmask, const snrmask_t *snrmask);
void SnrMask_save(JNIEnv* env, jobject j_snrmask, snrmask_t *dst);

jboolean processing_options_get_applcation_dir(JNIEnv* env, jobject thiz, char **dst){
	jclass clazz;
	jmethodID getApplicationDirectory;
	jstring j_path;
	jsize ssize=0;

	clazz = (*env)->FindClass(env, "gpsplus/rtkgps/MainActivity");
	if (clazz == NULL){
		trace(3,"ERROR FindClass gpsplus/rtkgps/MainActivity");
		return JNI_FALSE;}

	getApplicationDirectory = (*env)->GetStaticMethodID(env, clazz, "getApplicationDirectory",
			"()Ljava/lang/String;");

	if (getApplicationDirectory == NULL){
		trace(3,"ERROR GetStaticMethodID getApplicationDirectory\n");
		return JNI_FALSE;
	}

	j_path = (*env)->CallStaticObjectMethod(env, clazz, getApplicationDirectory);
	if (j_path == NULL){
		trace(3,"ERROR CallStaticObjectMethod getApplicationDirectory\n");
		return JNI_FALSE;
	}

	ssize = (*env)->GetStringUTFLength(env, j_path);

	*dst = (char*)malloc((ssize+1)*sizeof(char));
	if (*dst == NULL){
		trace(3,"ERROR malloc dst\n");
		return JNI_FALSE;
	}
	j_str2buf(env, j_path, *dst, (ssize+1));

	return JNI_TRUE;
}
void processing_options2prcopt_t(JNIEnv* env, jobject thiz, prcopt_t *dst)
{
   int string_size;
   jstring jstr;
   jobject jarr;
   jobject jsnrmask;
   jclass clazz;
   pcvs_t pcvr={0};
   pcv_t *pcv;
   gtime_t gtime=timeget();
   int i;
   #define IGS_08_RELATIVE_PATH "/files/data/igs08.atx"
   char *antexPath;
   char *antexFile;

   clazz = (*env)->FindClass(env, PROCESSING_OPTIONS_CLASS);
   if (clazz == NULL)
      return;
   if (!(*env)->IsInstanceOf(env, thiz, clazz)) {
      (*env)->ThrowNew(env, (*env)->FindClass(env, "Ljava/lang/ClassCastException"), NULL);
      return;
   }

   *dst = prcopt_default;

#define GET_FIELD(_name, _type) { dst->_name = (*env)->Get ## _type ## Field(env, thiz, gpsplus_rtklib_prcopt_fields._name); }
#define GET_FIELD2(_name, _type) (*env)->Get ## _type ## Field(env, thiz, gpsplus_rtklib_prcopt_fields._name);
   GET_FIELD(mode, Int)
   GET_FIELD(soltype, Int)
   GET_FIELD(nf, Int)
   GET_FIELD(navsys, Int)
   GET_FIELD(elmin, Double)
   GET_FIELD(sateph, Int)
   GET_FIELD(modear, Int)
   GET_FIELD(glomodear, Int)

   GET_FIELD(gpsmodear, Int)
   GET_FIELD(bdsmodear, Int)
   GET_FIELD(arfilter, Int)
   GET_FIELD(minfixsats, Int)
   GET_FIELD(minholdsats, Int)
   GET_FIELD(mindropsats, Int)
   GET_FIELD(rcvstds, Int)
   GET_FIELD(armaxiter, Int)
   GET_FIELD(varholdamb, Double)
   GET_FIELD(gainholdamb, Double)
   GET_FIELD(maxaveep, Int)
   GET_FIELD(initrst, Int)
   GET_FIELD(outsingle, Int)
   GET_FIELD(syncsol, Int)
   GET_FIELD(freqopt, Int)

   GET_FIELD(maxout, Int)
   GET_FIELD(minlock, Int)
   GET_FIELD(minfix, Int)
   GET_FIELD(ionoopt, Int)
   GET_FIELD(tropopt, Int)
   GET_FIELD(dynamics, Int)
   GET_FIELD(tidecorr, Int)
   GET_FIELD(niter, Int)
   GET_FIELD(codesmooth, Int)
   GET_FIELD(intpref, Int)
   GET_FIELD(sbascorr, Int)
   GET_FIELD(sbassatsel, Int)
   GET_FIELD(rovpos, Int)
   GET_FIELD(refpos, Int)
   dst->eratio[0] = GET_FIELD2(eratioL1, Double)
   dst->eratio[1] = GET_FIELD2(eratioL2, Double)
   dst->eratio[2] = GET_FIELD2(eratioL5, Double)
   /* dst->err[0] = GET_FIELD2(errPhaseA, Double) */
   dst->err[1] = GET_FIELD2(errPhaseA, Double)
   dst->err[2] = GET_FIELD2(errPhaseB, Double)
   dst->err[3] = GET_FIELD2(errPhaseC, Double)
   dst->err[4] = GET_FIELD2(errDopplerFreq, Double)
   dst->std[0] = GET_FIELD2(stdBias, Double)
   dst->std[1] = GET_FIELD2(stdIono, Double)
   dst->std[2] = GET_FIELD2(stdTrop, Double)
   dst->prn[0] = GET_FIELD2(prnBias, Double)
   dst->prn[1] = GET_FIELD2(prnIono, Double)
   dst->prn[2] = GET_FIELD2(prnTrop, Double)
   dst->prn[3] = GET_FIELD2(prnAcch, Double)
   dst->prn[4] = GET_FIELD2(prnAccv, Double)
   GET_FIELD(sclkstab, Double)
   dst->thresar[0] = GET_FIELD2(thresar_0, Double)
   dst->thresar[1] = GET_FIELD2(thresar_1, Double)
   dst->thresar[2] = GET_FIELD2(thresar_2, Double)
   dst->thresar[3] = GET_FIELD2(thresar_3, Double)
   GET_FIELD(elmaskar, Double)
   GET_FIELD(elmaskhold, Double)
   GET_FIELD(thresslip, Double)
   GET_FIELD(maxtdiff, Double)
   GET_FIELD(maxinno, Double)
   GET_FIELD(maxgdop, Double)
   dst->baseline[0] = GET_FIELD2(baselineConst, Double)
   dst->baseline[1] = GET_FIELD2(baselineSigma, Double)
   dst->ru[0] = GET_FIELD2(ruX, Double)
   dst->ru[1] = GET_FIELD2(ruY, Double)
   dst->ru[2] = GET_FIELD2(ruZ, Double)
   dst->rb[0] = GET_FIELD2(baseX, Double)
   dst->rb[1] = GET_FIELD2(baseY, Double)
   dst->rb[2] = GET_FIELD2(baseZ, Double)

   jstr = GET_FIELD2(anttypeRover, Object)
   j_str2buf(env, jstr, dst->anttype[0], sizeof(dst->anttype[0]));

   jstr = GET_FIELD2(anttypeBase, Object)
   j_str2buf(env, jstr, dst->anttype[1], sizeof(dst->anttype[1]));


  processing_options_get_applcation_dir(env,thiz,&antexPath);

   if (antexPath) {
	   antexFile = (char*)malloc((strlen(antexPath)+strlen(IGS_08_RELATIVE_PATH)+2)*sizeof(char));
	   strcpy(antexFile,antexPath);
	   strcat(antexFile,IGS_08_RELATIVE_PATH);

	   if (antexFile) {
		   if ( (strlen(dst->anttype[0])>1) || (strlen(dst->anttype[1])>1) ){
			   if (!readpcv(antexFile,&pcvr)){
				   trace(3,"error reading antex antenna file: file=%s\n",antexFile);
			   }else{
				   for (i = 0; i < 2; ++i) {
					   if ((pcv=searchpcv(0,dst->anttype[i],gtime,&pcvr))) {
						   dst->pcvr[i]=*pcv;
						   trace(3,"rcv[%d] antenna \"%s\" found in file=%s\n",i,dst->pcvr[i].type,antexFile);
					   }
				   }
				   free(pcvr.pcv);
			   }
		   }else{
			   trace(3,"No antenna for rover nor for base");
		   }
		   free(antexFile);
	   }
	   free(antexPath);
   }

   dst->antdel[0][0] = GET_FIELD2(antdelRovE, Double)
   dst->antdel[0][1] = GET_FIELD2(antdelRovN, Double)
   dst->antdel[0][2] = GET_FIELD2(antdelRovU, Double)
   dst->antdel[1][0] = GET_FIELD2(antdelRefE, Double)
   dst->antdel[1][1] = GET_FIELD2(antdelRefN, Double)
   dst->antdel[1][2] = GET_FIELD2(antdelRefU, Double)

   {
      /* exsats */
      int i;
      jboolean *j_bool_arr;

      jarr = GET_FIELD2(exsats, Object)
      j_bool_arr = (*env)->GetBooleanArrayElements(env, jarr, NULL);
      if (j_bool_arr == NULL) return;
      for (i=0; i<sizeof(dst->exsats)/sizeof(dst->exsats[0]); ++i)
	 dst->exsats[i] = j_bool_arr[i] ? 1 : 2;
      (*env)->ReleaseBooleanArrayElements(env, jarr, j_bool_arr, 0);
   }
   jstr = GET_FIELD2(rnxoptBase, Object)
   j_str2buf(env, jstr, dst->rnxopt[0], sizeof(dst->rnxopt[0]));
   jstr = GET_FIELD2(rnxoptRover, Object)
   j_str2buf(env, jstr, dst->rnxopt[1], sizeof(dst->rnxopt[1]));
   jarr = GET_FIELD2(posopt, Object)
   (*env)->GetIntArrayRegion(env, jarr, 0, sizeof(dst->posopt)/sizeof(dst->posopt[0]), dst->posopt);

   jsnrmask = GET_FIELD2(snrmask, Object);
   SnrMask_save(env, jsnrmask, &dst->snrmask);

#undef GET_FIELD
#undef GET_FIELD2

}

static void ProcessingOptions_load_defaults(JNIEnv* env, jobject thiz)
{
   jstring jstr;
   jobject jarr, jobj;
   const prcopt_t *src = &prcopt_default;

   if (gpsplus_rtklib_prcopt_fields.mode == NULL) {
      if ( ! init_prcopt_fields_methods(env, (*env)->GetObjectClass(env, thiz)))
	 return;
   }

#define SET_FIELD(_name, _type) { (*env)->Set ## _type ## Field(env, thiz, gpsplus_rtklib_prcopt_fields._name, src->_name); }
#define SET_FIELD2(_name, _type, _value) { (*env)->Set ## _type ## Field(env, thiz, gpsplus_rtklib_prcopt_fields._name, _value); }
   SET_FIELD(mode, Int)
   SET_FIELD(soltype, Int)
   SET_FIELD(nf, Int)
   SET_FIELD(navsys, Int)
   SET_FIELD(elmin, Double)
   {
      // SnrMask
      jobj = (*env)->GetObjectField(env, thiz, gpsplus_rtklib_prcopt_fields.snrmask);
      if (!SnrMask_load(env, jobj, &src->snrmask))
	 return;
   }
   SET_FIELD(sateph, Int)
   SET_FIELD(modear, Int)
   SET_FIELD(glomodear, Int)

   SET_FIELD(gpsmodear, Int)
   SET_FIELD(bdsmodear, Int)
   SET_FIELD(arfilter, Int)
   SET_FIELD(minfixsats, Int)
   SET_FIELD(minholdsats, Int)
   SET_FIELD(mindropsats, Int)
   SET_FIELD(rcvstds, Int)
   SET_FIELD(armaxiter, Int)
   SET_FIELD(varholdamb, Double)
   SET_FIELD(gainholdamb, Double)
   SET_FIELD(maxaveep, Int)
   SET_FIELD(initrst, Int)
   SET_FIELD(outsingle, Int)
   SET_FIELD(syncsol, Int)
   SET_FIELD(freqopt, Int)

   SET_FIELD(maxout, Int)
   SET_FIELD(minlock, Int)
   SET_FIELD(minfix, Int)
   SET_FIELD(ionoopt, Int)
   SET_FIELD(tropopt, Int)
   SET_FIELD(dynamics, Int)
   SET_FIELD(tidecorr, Int)
   SET_FIELD(niter, Int)
   SET_FIELD(codesmooth, Int)
   SET_FIELD(intpref, Int)
   SET_FIELD(sbascorr, Int)
   SET_FIELD(sbassatsel, Int)
   SET_FIELD(rovpos, Int)
   SET_FIELD(refpos, Int)
   SET_FIELD2(eratioL1, Double, src->eratio[0])
   SET_FIELD2(eratioL2, Double, src->eratio[1])
   SET_FIELD2(eratioL5, Double, src->eratio[2])
   SET_FIELD2(errPhaseA, Double, src->err[1])
   SET_FIELD2(errPhaseB, Double, src->err[2])
   SET_FIELD2(errPhaseC, Double, src->err[3])
   SET_FIELD2(errDopplerFreq, Double, src->err[4])
   SET_FIELD2(stdBias, Double, src->std[0])
   SET_FIELD2(stdIono, Double, src->std[1])
   SET_FIELD2(stdTrop, Double, src->std[2])
   SET_FIELD2(prnBias, Double, src->prn[0])
   SET_FIELD2(prnIono, Double, src->prn[1])
   SET_FIELD2(prnTrop, Double, src->prn[2])
   SET_FIELD2(prnAcch, Double, src->prn[3])
   SET_FIELD2(prnAccv, Double, src->prn[4])
   SET_FIELD(sclkstab, Double)
   SET_FIELD2(thresar_0, Double, src->thresar[0])
   SET_FIELD2(thresar_1, Double, src->thresar[1])
   SET_FIELD2(thresar_2, Double, src->thresar[2])
   SET_FIELD2(thresar_3, Double, src->thresar[3])
   SET_FIELD(elmaskar, Double)
   SET_FIELD(elmaskhold, Double)
   SET_FIELD(thresslip, Double)
   SET_FIELD(maxtdiff, Double)
   SET_FIELD(maxinno, Double)
   SET_FIELD(maxgdop, Double)
   SET_FIELD2(baselineConst, Double, src->baseline[0])
   SET_FIELD2(baselineSigma, Double, src->baseline[1])
   SET_FIELD2(ruX, Double, src->ru[0])
   SET_FIELD2(ruY, Double, src->ru[1])
   SET_FIELD2(ruZ, Double, src->ru[2])
   SET_FIELD2(baseX, Double, src->rb[0])
   SET_FIELD2(baseY, Double, src->rb[1])
   SET_FIELD2(baseZ, Double, src->rb[2])
   jstr = (*env)->NewStringUTF(env, src->anttype[0]);
   if (jstr == NULL) return;
   SET_FIELD2(anttypeBase, Object, jstr)
   jstr = (*env)->NewStringUTF(env, src->anttype[1]);
   if (jstr == NULL) return;
   SET_FIELD2(anttypeRover, Object, jstr)
   SET_FIELD2(antdelRovE, Double, src->antdel[0][0])
   SET_FIELD2(antdelRovN, Double, src->antdel[0][1])
   SET_FIELD2(antdelRovU, Double, src->antdel[0][2])
   SET_FIELD2(antdelRefE, Double, src->antdel[1][0])
   SET_FIELD2(antdelRefN, Double, src->antdel[1][1])
   SET_FIELD2(antdelRefU, Double, src->antdel[1][2])
   {
      /* exsats */
      int i;
      jboolean *j_bool_arr;

      jarr = (*env)->GetObjectField(env, thiz, gpsplus_rtklib_prcopt_fields.exsats);
      j_bool_arr = (*env)->GetBooleanArrayElements(env, jarr, NULL);
      if (j_bool_arr == NULL) return;
      for (i=0; i<sizeof(src->exsats)/sizeof(src->exsats[0]); ++i)
	 j_bool_arr[i] = src->exsats[i] == 1 ? JNI_TRUE : JNI_FALSE;
      (*env)->ReleaseBooleanArrayElements(env, jarr, j_bool_arr, 0);
   }
   // rnxoptBase
   jstr = (*env)->NewStringUTF(env, src->rnxopt[0]);
   if (jstr == NULL) return;
   SET_FIELD2(rnxoptBase, Object, jstr)
   // rnxoptRover
   jstr = (*env)->NewStringUTF(env, src->rnxopt[1]);
   SET_FIELD2(rnxoptRover, Object, jstr)
   // posopt
   jarr = (*env)->GetObjectField(env, thiz, gpsplus_rtklib_prcopt_fields.posopt);
   (*env)->SetIntArrayRegion(env, jarr, 0,
	 sizeof(src->posopt)/sizeof(src->posopt[0]), src->posopt);

#undef SET_FIELD
#undef SET_FIELD2

}

jboolean SnrMask_load(JNIEnv* env, jobject j_snrmask, const snrmask_t *snrmask)
{
   jarray j_arr_l1, j_arr_l2, j_arr_l5;

   if (snrmask == NULL)
      return JNI_FALSE;

   (*env)->SetBooleanField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.enableRover, snrmask->ena[0]);
   (*env)->SetBooleanField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.enableBase, snrmask->ena[1]);

   j_arr_l1 = (*env)->GetObjectField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.maskL1);
   j_arr_l2 = (*env)->GetObjectField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.maskL2);
   j_arr_l5 = (*env)->GetObjectField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.maskL5);
   (*env)->SetDoubleArrayRegion(env, j_arr_l1, 0, sizeof(snrmask->mask[0])/sizeof(snrmask->mask[0][0]), snrmask->mask[0]);
   (*env)->SetDoubleArrayRegion(env, j_arr_l2, 0, sizeof(snrmask->mask[1])/sizeof(snrmask->mask[1][0]), snrmask->mask[1]);
   (*env)->SetDoubleArrayRegion(env, j_arr_l5, 0, sizeof(snrmask->mask[2])/sizeof(snrmask->mask[2][0]), snrmask->mask[2]);

   return JNI_TRUE;
}

void SnrMask_save(JNIEnv* env, jobject j_snrmask, snrmask_t *dst)
{
   jarray j_arr_l1, j_arr_l2, j_arr_l5;
   if (dst == NULL)
      return;

   dst->ena[0] = (*env)->GetBooleanField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.enableRover);
   dst->ena[1] = (*env)->GetBooleanField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.enableBase);

   j_arr_l1 = (*env)->GetObjectField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.maskL1);
   j_arr_l2 = (*env)->GetObjectField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.maskL2);
   j_arr_l5 = (*env)->GetObjectField(env, j_snrmask, gpsplus_rtklib_snrmask_fields.maskL5);
   (*env)->GetDoubleArrayRegion(env, j_arr_l1, 0, sizeof(dst->mask[0])/sizeof(dst->mask[0][0]), dst->mask[0]);
   (*env)->GetDoubleArrayRegion(env, j_arr_l2, 0, sizeof(dst->mask[1])/sizeof(dst->mask[1][0]), dst->mask[1]);
   (*env)->GetDoubleArrayRegion(env, j_arr_l5, 0, sizeof(dst->mask[2])/sizeof(dst->mask[2][0]), dst->mask[2]);

}



static int init_prcopt_fields_methods(JNIEnv* env, jclass clazz)
{

#define INIT_FIELD(_name, _type) { \
      gpsplus_rtklib_prcopt_fields._name = (*env)->GetFieldID(env, clazz, #_name, _type); \
      if (gpsplus_rtklib_prcopt_fields._name == NULL) { \
	 LOGV("gpsplus/rtklib/ProcessingOptions$Native$%s not found", #_name); \
	 return JNI_FALSE; \
      } \
      }

   INIT_FIELD(mode, "I")
   INIT_FIELD(soltype, "I")
   INIT_FIELD(nf, "I")
   INIT_FIELD(navsys, "I")
   INIT_FIELD(elmin, "D")
   INIT_FIELD(snrmask, "Lgpsplus/rtklib/ProcessingOptions$SnrMask;")
   INIT_FIELD(sateph, "I")
   INIT_FIELD(modear, "I")
   INIT_FIELD(glomodear, "I")

   INIT_FIELD(gpsmodear, "I")
   INIT_FIELD(bdsmodear, "I")
   INIT_FIELD(arfilter, "I")
   INIT_FIELD(minfixsats, "I")
   INIT_FIELD(minholdsats, "I")
   INIT_FIELD(mindropsats, "I")
   INIT_FIELD(rcvstds, "I")
   INIT_FIELD(armaxiter, "I")
   INIT_FIELD(varholdamb, "D")
   INIT_FIELD(gainholdamb, "D")
   INIT_FIELD(maxaveep, "I")
   INIT_FIELD(initrst, "I")
   INIT_FIELD(outsingle, "I")
   INIT_FIELD(syncsol, "I")
   INIT_FIELD(freqopt, "I")

   INIT_FIELD(maxout, "I")
   INIT_FIELD(minlock, "I")
   INIT_FIELD(minfix, "I")
   INIT_FIELD(ionoopt, "I")
   INIT_FIELD(tropopt, "I")
   INIT_FIELD(dynamics, "I")
   INIT_FIELD(tidecorr, "I")
   INIT_FIELD(niter, "I")
   INIT_FIELD(codesmooth, "I")
   INIT_FIELD(intpref, "I")
   INIT_FIELD(sbascorr, "I")
   INIT_FIELD(sbassatsel, "I")
   INIT_FIELD(rovpos, "I")
   INIT_FIELD(refpos, "I")
   INIT_FIELD(eratioL1, "D")
   INIT_FIELD(eratioL2, "D")
   INIT_FIELD(eratioL5, "D")
   INIT_FIELD(errPhaseA, "D")
   INIT_FIELD(errPhaseB, "D")
   INIT_FIELD(errPhaseC, "D")
   INIT_FIELD(errDopplerFreq, "D")
   INIT_FIELD(stdBias, "D")
   INIT_FIELD(stdIono, "D")
   INIT_FIELD(stdTrop, "D")
   INIT_FIELD(prnBias, "D")
   INIT_FIELD(prnIono, "D")
   INIT_FIELD(prnTrop, "D")
   INIT_FIELD(prnAcch, "D")
   INIT_FIELD(prnAccv, "D")
   INIT_FIELD(sclkstab, "D")
   INIT_FIELD(thresar_0, "D")
   INIT_FIELD(thresar_1, "D")
   INIT_FIELD(thresar_2, "D")
   INIT_FIELD(thresar_3, "D")
   INIT_FIELD(elmaskar, "D")
   INIT_FIELD(elmaskhold, "D")
   INIT_FIELD(thresslip, "D")
   INIT_FIELD(maxtdiff, "D")
   INIT_FIELD(maxinno, "D")
   INIT_FIELD(maxgdop, "D")
   INIT_FIELD(baselineConst, "D")
   INIT_FIELD(baselineSigma, "D")
   INIT_FIELD(ruX, "D")
   INIT_FIELD(ruY, "D")
   INIT_FIELD(ruZ, "D")
   INIT_FIELD(baseX, "D")
   INIT_FIELD(baseY, "D")
   INIT_FIELD(baseZ, "D")
   INIT_FIELD(anttypeBase, "Ljava/lang/String;")
   INIT_FIELD(anttypeRover, "Ljava/lang/String;")
   INIT_FIELD(antdelRovE, "D")
   INIT_FIELD(antdelRovN, "D")
   INIT_FIELD(antdelRovU, "D")
   INIT_FIELD(antdelRefE, "D")
   INIT_FIELD(antdelRefN, "D")
   INIT_FIELD(antdelRefU, "D")
   INIT_FIELD(exsats, "[Z")
   INIT_FIELD(rnxoptBase, "Ljava/lang/String;")
   INIT_FIELD(rnxoptRover, "Ljava/lang/String;")
   INIT_FIELD(posopt, "[I")

#undef INIT_FIELD

   return JNI_TRUE;
}

static int init_snrmask_fields_methods(JNIEnv* env, jclass clazz)
{
#define INIT_FIELD(_name, _type) { \
      gpsplus_rtklib_snrmask_fields._name = (*env)->GetFieldID(env, clazz, #_name, _type); \
      if (gpsplus_rtklib_snrmask_fields._name == NULL) { \
	 LOGV("gpsplus/rtklib/ProcessingOptions$SnrMask$%s not found", #_name); \
	 return JNI_FALSE; \
      } \
      }

   INIT_FIELD(enableRover, "Z")
   INIT_FIELD(enableBase, "Z")
   INIT_FIELD(maskL1, "[D")
   INIT_FIELD(maskL2, "[D")
   INIT_FIELD(maskL5, "[D")
#undef INIT_FIELD

   return JNI_TRUE;
}


static JNINativeMethod nativeMethods[] = {
   { "_loadDefaults", "()V", (void*)ProcessingOptions_load_defaults }
};


int registerProcessingOptionsNatives(JNIEnv* env) {
   jclass prcopt_clazz, snrmask_clazz;
   int result = -1;

    /* look up the class */
    prcopt_clazz = (*env)->FindClass(env, PROCESSING_OPTIONS_CLASS);
    snrmask_clazz = (*env)->FindClass(env, SNR_MASK_CLASS);

    if (prcopt_clazz == NULL)
       return JNI_FALSE;

    if ((*env)->RegisterNatives(env, prcopt_clazz, nativeMethods, sizeof(nativeMethods)
	     / sizeof(nativeMethods[0])) != JNI_OK)
       return JNI_FALSE;

    if ( ! init_prcopt_fields_methods(env, prcopt_clazz))
       return JNI_FALSE;

    if ( ! init_snrmask_fields_methods(env, snrmask_clazz))
       return JNI_FALSE;

    return JNI_TRUE;
}

