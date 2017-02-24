
#include <android/log.h>
#include <jni.h>
#include <strings.h>

#include "rtklib.h"
#include "rtkjni.h"

#define TAG "nativeRtkServer"
#define LOGV(...) showmsg(__VA_ARGS__)

static jfieldID m_object_field;

static struct {
   jfieldID ns;
   jfieldID time;
   jfieldID sat;
   jfieldID az;
   jfieldID el;
   jfieldID freq1_snr;
   jfieldID freq2_snr;
   jfieldID freq3_snr;
   jfieldID vsat;
} obs_status_fields;

struct native_ctx_t {
   rtksvr_t rtksvr;                        // rtk server struct
   stream_t monistr;                       // monitor stream
};


static jboolean open_trace_file(JNIEnv* env, int trace_level, gtime_t timestamp);
static jboolean open_solution_status_file(JNIEnv* env, int level, gtime_t timestamp);

static void RtkServer__create(JNIEnv* env, jobject thiz)
{
   struct native_ctx_t *nctx;

   LOGV("RtkServer__create()");

   nctx = (struct native_ctx_t *)calloc(1, sizeof(struct native_ctx_t));

   if (nctx == NULL) {
      LOGV("calloc() error");
      return;
   }

   rtksvrinit(&nctx->rtksvr);
   strinit(&nctx->monistr);

   (*env)->SetLongField(env, thiz, m_object_field, (long)nctx);
}

static void RtkServer__destroy(JNIEnv* env, jobject thiz)
{
   struct native_ctx_t *nctx;
   char *cmds[3]={0,};

   LOGV("RtkServer__destroy()");

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }
   rtksvrstop(&nctx->rtksvr,cmds);
   rtksvrfree(&nctx->rtksvr);

   free(nctx);
   (*env)->SetLongField(env, thiz, m_object_field, 0L);
}

static jboolean RtkServer__rtksvrstart(JNIEnv* env, jclass thiz,
      jint j_cycle,
      jint j_buffsize,
      jintArray j_strs,
      jobjectArray j_paths,
      jintArray j_format,
      jint j_navsel,
      jobjectArray j_cmds,
      jobjectArray j_rcvopts,
      jint j_nmea_cycle,
      jint j_nmea_req,
      jdoubleArray j_nmeapos,
      jobject j_procopt,
      jobject j_solopt1,
      jobject j_solopt2
      ) {

   struct native_ctx_t *nctx;
   int i;
   jobject obj;
   jboolean res;

   /* Input stream types */
   int strs[8];
   /* input stream paths */
   const char *paths[8] = {NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL };
   jstring paths_jstring[8] = {NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL };
   /* input stream formats (STRFMT_???) */
   int format[3];
   /* input stream start commands */
   const char *cmds[3] = {NULL, NULL, NULL};
   jstring cmds_jstring[3] = {NULL, NULL, NULL};
   /* receiver options */
   const char *rcvopts[3] = {NULL, NULL, NULL};
   char *rcvopts_jstring[3] = {NULL, NULL, NULL};
   /* rtk processing options */
   prcopt_t prcopt;
   /* solution options */
   solopt_t solopt[2];
   /* nmea position */
   double nmeapos[3];

   res = JNI_FALSE;
   for (i=0; i<sizeof(paths)/sizeof(paths[0]); ++i) {
      paths_jstring[i] = (*env)->GetObjectArrayElement(env, j_paths, i);
      if ((*env)->ExceptionOccurred(env))
	 goto rtksvrstart_end;
      if (paths_jstring[i] != NULL) {
	 paths[i] = (*env)->GetStringUTFChars(env, paths_jstring[i], NULL);
	 if (paths[i] == NULL)
	    goto rtksvrstart_end;
      }
   }
   for (i=0; i<sizeof(cmds)/sizeof(cmds[0]); ++i) {
      cmds_jstring[i] = (*env)->GetObjectArrayElement(env, j_cmds, i);
      if ((*env)->ExceptionOccurred(env))
	 goto rtksvrstart_end;
      if (cmds_jstring[i] != NULL) {
	 cmds[i] = (*env)->GetStringUTFChars(env, cmds_jstring[i], NULL);
	 if (cmds[i] == NULL)
	    goto rtksvrstart_end;
      }
   }
   for (i=0; i<sizeof(rcvopts)/sizeof(rcvopts[0]); ++i) {
      rcvopts_jstring[i] = (*env)->GetObjectArrayElement(env, j_rcvopts, i);
      if ((*env)->ExceptionOccurred(env))
	 goto rtksvrstart_end;
      if (rcvopts_jstring[i] != NULL) {
	 rcvopts[i] = (*env)->GetStringUTFChars(env, rcvopts_jstring[i], NULL);
	 if (rcvopts[i] == NULL)
	    goto rtksvrstart_end;
      }
   }

   (*env)->GetIntArrayRegion(env, j_strs, 0,
	 sizeof(strs)/sizeof(strs[0]), strs);
   if ((*env)->ExceptionOccurred(env))
      goto rtksvrstart_end;

   (*env)->GetIntArrayRegion(env, j_format, 0,
	 sizeof(format)/sizeof(format[0]), format);
   if ((*env)->ExceptionOccurred(env))
      goto rtksvrstart_end;

   (*env)->GetDoubleArrayRegion(env, j_nmeapos, 0,
	 sizeof(nmeapos)/sizeof(nmeapos[0]), nmeapos);
   if ((*env)->ExceptionOccurred(env))
      goto rtksvrstart_end;

   solution_options2solopt_t(env, j_solopt1, &solopt[0]);
   solution_options2solopt_t(env, j_solopt2, &solopt[1]);

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      goto rtksvrstart_end;
   }

   {
      /* Open trace / solution files */
      gtime_t now;
      now = timeget();
      if (solopt[0].trace > 0)
	 open_trace_file(env, solopt[0].trace, now);

      /* treat processing options only now for having trace */
      processing_options2prcopt_t(env, j_procopt, &prcopt);

      if (solopt[0].sstat > 0)
	 open_solution_status_file(env, solopt[0].sstat, now);
   }

   if ((*env)->ExceptionOccurred(env))
      goto rtksvrstart_end;

    char errmsg[2048]="";                       //  Modif Mathieu Peyréga : adapt to new 2.4.3b26 API
    char *cmds_periodic[]={NULL,NULL,NULL};     //  Modif Mathieu Peyréga : adapt to new 2.4.3b26 API

   if (!rtksvrstart(
	    &nctx->rtksvr,
	    /* SvrCycle ms */ j_cycle,
	    /* SvrBuffSize */ j_buffsize,
	    /* stream types */ strs,
	    /* paths */ (char **)paths,
	    /* input stream format */ format,
	    /* NavSelect */ j_navsel,
	    /* input stream start commands */ (char **)cmds,
        /* input stream periodic  commands */ (char **)cmds_periodic,   //  Modif Mathieu Peyréga : adapt to new 2.4.3b26 API
	    /* receiver options */ (char **)rcvopts,
	    /* nmea request cycle ms */ j_nmea_cycle,
	    /* nmea request type */ j_nmea_req,
	    /* transmitted nmea position  */ nmeapos,
	    /* rtk processing options */&prcopt,
	    /* solution options */ solopt,
	    /* monitor stream */ &nctx->monistr,
        /* err */errmsg                                                  //  Modif Mathieu Peyréga : adapt to new 2.4.3b26 API
	    )) {
   }else {
      res = JNI_TRUE;
   }

rtksvrstart_end:

   for (i=0; i<sizeof(paths)/sizeof(paths[0]); ++i) {
      if (paths[i] != NULL)
	 (*env)->ReleaseStringUTFChars(env, paths_jstring[i], paths[i]);
   }
   for (i=0; i<sizeof(cmds)/sizeof(cmds[0]); ++i) {
      if (cmds[i] != NULL)
	 (*env)->ReleaseStringUTFChars(env, cmds_jstring[i], cmds[i]);
   }
   for (i=0; i<sizeof(rcvopts)/sizeof(rcvopts[0]); ++i) {
      if (rcvopts[i] != NULL)
	 (*env)->ReleaseStringUTFChars(env, rcvopts_jstring[i], rcvopts[i]);
   }

   if (!res) {
      traceclose();
      rtkclosestat();
   }

   return res;
}

static void RtkServer__stop(JNIEnv* env, jclass thiz, jobjectArray j_cmds)
{
   unsigned i;
   struct native_ctx_t *nctx;
   const char *cmds[3]={NULL, NULL, NULL};
   jstring cmds_jstring[3]={NULL, NULL, NULL};

   LOGV("RtkServer__stop()");

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }

   for (i=0; i<sizeof(cmds)/sizeof(cmds[0]); ++i) {
      cmds_jstring[i] = (*env)->GetObjectArrayElement(env, j_cmds, i);
      if ((*env)->ExceptionOccurred(env))
	 goto rtksvrstop_close;
      if (cmds_jstring[i] != NULL) {
	 cmds[i] = (*env)->GetStringUTFChars(env, cmds_jstring[i], NULL);
	 if (cmds[i] == NULL)
	    goto rtksvrstop_close;
      }
   }

rtksvrstop_close:

   rtksvrstop(&nctx->rtksvr,(char **)cmds);

   traceclose();
   rtkclosestat();

   for (i=0; i<sizeof(cmds)/sizeof(cmds[0]); ++i) {
      if (cmds[i] != NULL)
	 (*env)->ReleaseStringUTFChars(env, cmds_jstring[i], cmds[i]);
   }

}


static jboolean get_path_in_storage_dir(JNIEnv* env, char *filename, size_t bufsize)
{
   jclass clazz;
   jmethodID get_path_in_dir_mid;
   jstring j_filename;
   jstring j_path;

   clazz = (*env)->FindClass(env, "gpsplus/rtklib/RtkServer");
   if (clazz == NULL)
      return JNI_FALSE;

   get_path_in_dir_mid = (*env)->GetStaticMethodID(env, clazz, "getPathInStorageDirectory",
	 "(Ljava/lang/String;)Ljava/lang/String;");
   if (get_path_in_dir_mid == NULL)
      return JNI_FALSE;

   j_filename = (*env)->NewStringUTF(env, filename);
   if (j_filename == NULL)
      return JNI_FALSE;

   j_path = (*env)->CallStaticObjectMethod(env, clazz, get_path_in_dir_mid, j_filename);
   if (j_path == NULL)
      return JNI_FALSE;

   j_str2buf(env, j_path, filename, bufsize);

   return JNI_TRUE;
}

static jboolean open_trace_file(JNIEnv* env, int trace_level, gtime_t timestamp)
{
   double ep[6];
   char filename[1024];

   if (trace_level <= 0)
      return JNI_FALSE;

   time2epoch(utc2gpst(timestamp),ep);
   sprintf(filename,"rtkgps_%04.0f%02.0f%02.0f%02.0f%02.0f%02.0f.trace",
	 ep[0],ep[1],ep[2],ep[3],ep[4],ep[5]);
   if (!get_path_in_storage_dir(env, filename, sizeof(filename)))
      return JNI_FALSE;

   LOGV("open_trace_file() %s", filename);
   traceopen(filename);
   tracelevel(trace_level);

   return JNI_TRUE;
}

static jboolean open_solution_status_file(JNIEnv* env, int level, gtime_t timestamp)
{
   double ep[6];
   char filename[1024];

   if (level <= 0)
      return JNI_FALSE;

   time2epoch(utc2gpst(timestamp),ep);
   sprintf(filename,"rtkgps_%04.0f%02.0f%02.0f%02.0f%02.0f%02.0f.stat",
	 ep[0],ep[1],ep[2],ep[3],ep[4],ep[5]);
   if (!get_path_in_storage_dir(env, filename, sizeof(filename)))
      return JNI_FALSE;

   LOGV("open_solution_status_file() %s", filename);
   rtkopenstat(filename, level);

   return JNI_TRUE;
}

static void RtkServer__get_stream_status(JNIEnv* env, jclass thiz, jobject status_obj)
{
   struct native_ctx_t *nctx;
   jmethodID set_status_mid;
   jstring jmsg;
   int sstat[MAXSTRRTK];
   char msg[MAXSTRMSG] = {0,};

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }

   set_status_mid = (*env)->GetMethodID(env,
	 (*env)->GetObjectClass(env, status_obj),
	 "setStatus",
	 "(IIIIIIIILjava/lang/String;)V");
   if (set_status_mid == NULL) {
      LOGV("setStatus() not found");
      return;
   }

   rtksvrsstat(&nctx->rtksvr, sstat, msg);

   jmsg = (*env)->NewStringUTF(env, msg);
   if (jmsg == NULL)
      return;

   (*env)->CallVoidMethod(env, status_obj, set_status_mid,
	 (jint)sstat[0], (jint)sstat[1], (jint)sstat[2],
	 (jint)sstat[3], (jint)sstat[4], (jint)sstat[5],
	 (jint)sstat[6], (jint)sstat[7],
	 jmsg);

}

static void RtkServer__get_observation_status(JNIEnv* env, jclass thiz,
      jint receiver, jobject status_obj)
{
   struct native_ctx_t *nctx;
   jobject jgtime, jtmp;
   int ns;
   int i;
   jint *jsnr;
   gtime_t time;
   int sat[MAXSAT];
   int vsat[MAXSAT];
   double az[MAXSAT];
   double el[MAXSAT];
   int *snr0[MAXSAT];
   int snr[MAXSAT][NFREQ];

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }

   for (i=0;i<MAXSAT;i++) {
      snr0[i]=snr[i];
   }

   ns = rtksvrostat(&nctx->rtksvr, receiver,
	 &time, sat, az, el, snr0, vsat);

   if (ns > MAXSAT)
      ns = MAXSAT;

   // Set number of satellites
   (*env)->SetIntField(env, status_obj, obs_status_fields.ns, ns);

   if (ns == 0)
      return;

   // Set time
   jgtime = (*env)->GetObjectField(env, status_obj, obs_status_fields.time);
   if (jgtime == NULL) {
      LOGV("time is null");
   }
   set_gtime(env, jgtime, time);

   // Satellite data
   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.sat);
   (*env)->SetIntArrayRegion(env, jtmp, 0, ns, sat);

   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.az);
   (*env)->SetDoubleArrayRegion(env, jtmp, 0, ns, az);

   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.el);
   (*env)->SetDoubleArrayRegion(env, jtmp, 0, ns, el);

   // freq1 snr
   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.freq1_snr);
   jsnr = (*env)->GetIntArrayElements(env, jtmp, NULL);
   if (jsnr != NULL) {
      for (i=0; i<ns; ++i) jsnr[i] = snr[i][0];
      (*env)->ReleaseIntArrayElements(env, jtmp, jsnr, 0);
   }

   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.freq2_snr);
   jsnr = (*env)->GetIntArrayElements(env, jtmp, NULL);
   if (jsnr != NULL) {
      for (i=0; i<ns; ++i) jsnr[i] = snr[i][1];
      (*env)->ReleaseIntArrayElements(env, jtmp, jsnr, 0);
   }

   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.freq3_snr);
   jsnr = (*env)->GetIntArrayElements(env, jtmp, NULL);
   if (jsnr != NULL) {
      for (i=0; i<ns; ++i) jsnr[i] = snr[i][2];
      (*env)->ReleaseIntArrayElements(env, jtmp, jsnr, 0);
   }

   // vsat
   jtmp = (*env)->GetObjectField(env, status_obj, obs_status_fields.vsat);
   (*env)->SetIntArrayRegion(env, jtmp, 0, ns, vsat);

}

static void RtkServer__write_commands(JNIEnv* env, jclass thiz,
        jobjectArray j_cmds)
{
   unsigned i;
   struct native_ctx_t *nctx;
   const char *cmds[3] = {NULL, NULL, NULL};
   jstring cmds_jstring[3] = {NULL, NULL, NULL};

   LOGV("RtkServer__write_start_commands() ");

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }

   for (i=0; i<sizeof(cmds)/sizeof(cmds[0]); ++i) {
      cmds_jstring[i] = (*env)->GetObjectArrayElement(env, j_cmds, i);
      if ((*env)->ExceptionOccurred(env))
	 goto write_commands_end;
      if (cmds_jstring[i] != NULL) {
	 cmds[i] = (*env)->GetStringUTFChars(env, cmds_jstring[i], NULL);
	 if (cmds[i] == NULL)
	    goto write_commands_end;
      }
   }

   rtksvrlock(&nctx->rtksvr);
   if (cmds[0]) strsendcmd(&nctx->rtksvr.stream[0], cmds[0]);
   if (cmds[1]) strsendcmd(&nctx->rtksvr.stream[1], cmds[1]);
   if (cmds[2]) strsendcmd(&nctx->rtksvr.stream[2], cmds[2]);
   rtksvrunlock(&nctx->rtksvr);

write_commands_end:

   for (i=0; i<sizeof(cmds)/sizeof(cmds[0]); ++i) {
      if (cmds[i] != NULL)
	 (*env)->ReleaseStringUTFChars(env, cmds_jstring[i], cmds[i]);
   }

}


static int set_solution_buffer(JNIEnv* env, jobject j_solbuf, const sol_t *solutions, int cnt)
{
   int i;
   static jfieldID m_n_sol_field = NULL;
   static jmethodID set_solution_method = NULL;

   if (m_n_sol_field == NULL) {
      jobject claz = (*env)->GetObjectClass(env, j_solbuf);
      m_n_sol_field = (*env)->GetFieldID(env, claz, "mNSol", "I");
      if (m_n_sol_field == NULL) {
	 LOGV("SolutionBuffer$mNSol field not found");
	 return -1;
      }
      set_solution_method = (*env)->GetMethodID(env,
	    claz,
	    "setSolution",
	    "(IJDIIIFFDDDDDDFFFFFFDDDDDD)V"
	    );
      if (set_solution_method == NULL) {
	 LOGV("setSolution() not found");
	 return -1;
      }
   }

   for (i=0; i<cnt; ++i) {
      const sol_t *s = &solutions[i];
      (*env)->CallVoidMethod(env, j_solbuf, set_solution_method,
	    i,
	    (jlong)s->time.time,
	    (jlong)s->time.sec,
	    (jint)s->type,
	    (jint)s->stat,
	    (jint)s->ns,
	    (jfloat)s->age,
	    (jfloat)s->ratio,
	    (jdouble)s->rr[0], (jdouble)s->rr[1], (jdouble)s->rr[2],
	    (jdouble)s->rr[3], (jdouble)s->rr[4], (jdouble)s->rr[5],
	    (jfloat)s->qr[0], (jfloat)s->qr[1], (jfloat)s->qr[2],
	    (jfloat)s->qr[3], (jfloat)s->qr[4], (jfloat)s->qr[5],
	    (jdouble)s->dtr[0], (jdouble)s->dtr[1], (jdouble)s->dtr[2],
	    (jdouble)s->dtr[3], (jdouble)s->dtr[4], (jdouble)s->dtr[5]
	    );
   }

   (*env)->SetIntField(env, j_solbuf, m_n_sol_field, cnt);

   return cnt;
}

static void RtkServer__read_solution_buffer(JNIEnv* env, jclass thiz,
      jobject j_solbuf)
{
   struct native_ctx_t *nctx;

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }

   rtksvrlock(&nctx->rtksvr);
   set_solution_buffer(env, j_solbuf, nctx->rtksvr.solbuf, nctx->rtksvr.nsol);
   nctx->rtksvr.nsol=0;
   rtksvrunlock(&nctx->rtksvr);
}

static int set_rtk_status(JNIEnv* env, jobject j_rtk_control_result, rtk_t *status)
{
   static jfieldID sol_field_id = NULL;
   static jmethodID set_solution_method_id = NULL;
   static jmethodID set_status_method_id = NULL;
   jobject sol_obj;

   if (sol_field_id == NULL) {
      jobject claz = (*env)->GetObjectClass(env, j_rtk_control_result);
      sol_field_id = (*env)->GetFieldID(env, claz, "sol", "Lgpsplus/rtklib/Solution;");
      if (sol_field_id == NULL) {
	 LOGV("Solution field not found");
	 return -1;
      }
   }
   if (set_status_method_id == NULL) {
      jobject claz = (*env)->GetObjectClass(env, j_rtk_control_result);
      set_status_method_id = (*env)->GetMethodID(env,
	    claz,
	    "setStatus1",
	    "(DDDDDDIIDILjava/lang/String;)V"
	    );
      if (set_status_method_id == NULL) {
	 LOGV("setStatus1() not found");
	 return -1;
      }
   }

   sol_obj = (*env)->GetObjectField(env, j_rtk_control_result, sol_field_id);

   if (set_solution_method_id == NULL) {
      jobject claz = (*env)->GetObjectClass(env, sol_obj);
      set_solution_method_id = (*env)->GetMethodID(env,
	    claz,
	    "setSolution",
	    "(JDIIIFFDDDDDDFFFFFFDDDDDD)V"
	    );
      if (set_solution_method_id == NULL) {
	 LOGV("setSolution() not found");
	 return -1;
      }
   }

   // solution
   (*env)->CallVoidMethod(env, sol_obj, set_solution_method_id,
	 (jlong)status->sol.time.time,
	 (jlong)status->sol.time.sec,
	 (jint)status->sol.type,
	 (jint)status->sol.stat,
	 (jint)status->sol.ns,
	 (jfloat)status->sol.age,
	 (jfloat)status->sol.ratio,
	 (jdouble)status->sol.rr[0], (jdouble)status->sol.rr[1], (jdouble)status->sol.rr[2],
	 (jdouble)status->sol.rr[3], (jdouble)status->sol.rr[4], (jdouble)status->sol.rr[5],
	 (jfloat)status->sol.qr[0], (jfloat)status->sol.qr[1], (jfloat)status->sol.qr[2],
	 (jfloat)status->sol.qr[3], (jfloat)status->sol.qr[4], (jfloat)status->sol.qr[5],
	 (jdouble)status->sol.dtr[0], (jdouble)status->sol.dtr[1], (jdouble)status->sol.dtr[2],
	 (jdouble)status->sol.dtr[3], (jdouble)status->sol.dtr[4], (jdouble)status->sol.dtr[5]
	 );

   // RTK fields
   {
      jstring errmsg;
      if (status->neb == 0) {
	 errmsg = (*env)->NewStringUTF(env, "");
      }else {
	 status->errbuf[status->neb-1] = '\0';
	 errmsg = (*env)->NewStringUTF(env, status->errbuf);
      }
      if (errmsg == NULL)
	 return -1;

      (*env)->CallVoidMethod(env, j_rtk_control_result, set_status_method_id,
	 (jdouble)status->rb[0], (jdouble)status->rb[1], (jdouble)status->rb[2],
	 (jdouble)status->rb[3], (jdouble)status->rb[4], (jdouble)status->rb[5],
	 status->nx, status->na, (jdouble)status->tt, status->nfix,
	 errmsg);
   }

   return 0;
}

static void RtkServer__get_rtk_status(JNIEnv* env, jclass thiz, jobject j_rtk_control_result)
{
   struct native_ctx_t *nctx;

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
      LOGV("nctx is null");
      return;
   }

   rtksvrlock(&nctx->rtksvr);
   set_rtk_status(env, j_rtk_control_result, &nctx->rtksvr.rtk);
   rtksvrunlock(&nctx->rtksvr);
}

static void RtkServer__readsp3(JNIEnv* env, jclass thiz, jstring file)
{
   struct native_ctx_t *nctx;
   nav_t nav={0};
   rtksvr_t *svr;

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
	  LOGV("nctx is null");
	  return;
   }
	const char *filename = (*env)->GetStringUTFChars(env, file, 0);
	readsp3(filename,&nav,0);
	/* test if there is some ephemeris */
	if (nav.ne<=0) {
	            tracet(1,"sp3 file read error: %s\n",file);
	            return;
	        }

	//OK so update to server
	svr = &nctx->rtksvr;
	rtksvrlock(svr);
    if (svr->nav.peph) free(svr->nav.peph);
    svr->nav.ne=svr->nav.nemax=nav.ne;
    svr->nav.peph=nav.peph;

	rtksvrunlock(svr);
	(*env)->ReleaseStringUTFChars(env,file, filename);
}

static void RtkServer__readsatant(JNIEnv* env, jclass thiz, jstring file)
{
   struct native_ctx_t *nctx;
   pcvs_t pcvs={0};
   pcv_t *pcv;
   int i;
   gtime_t now=timeget();

   nctx = (struct native_ctx_t *)(uintptr_t)(*env)->GetLongField(env, thiz, m_object_field);
   if (nctx == NULL) {
	  LOGV("nctx is null");
	  return;
   }
	const char *filename = (*env)->GetStringUTFChars(env, file, 0);
	rtksvrlock(&nctx->rtksvr);

    if (readpcv(filename,&pcvs)) {
        for (i=0;i<MAXSAT;i++) {
            if (!(pcv=searchpcv(i+1,"",now,&pcvs))) continue;
            nctx->rtksvr.nav.pcvs[i]=*pcv;
        }
    }

	rtksvrunlock(&nctx->rtksvr);
	(*env)->ReleaseStringUTFChars(env,file, filename);
}

static JNINativeMethod nativeMethods[] = {
   {"_create", "()V", (void*)RtkServer__create},
   {"_destroy", "()V", (void*)RtkServer__destroy},
   {"_rtksvrstart", "("
	 "I"
	 "I"
	 "[I"
	 "[Ljava/lang/String;"
	 "[I"
	 "I"
	 "[Ljava/lang/String;"
	 "[Ljava/lang/String;"
	 "I"
	 "I"
	 "[D"
	 "Lgpsplus/rtklib/ProcessingOptions$Native;"
	 "Lgpsplus/rtklib/SolutionOptions$Native;"
	 "Lgpsplus/rtklib/SolutionOptions$Native;"
	 ")Z"
	 , (void*)RtkServer__rtksvrstart},
   {"_stop", "([Ljava/lang/String;)V", (void*)RtkServer__stop},
   {"_getStreamStatus", "(Lgpsplus/rtklib/RtkServerStreamStatus;)V", (void*)RtkServer__get_stream_status},
   {"_readSolutionBuffer", "(Lgpsplus/rtklib/Solution$SolutionBuffer;)V", (void*)RtkServer__read_solution_buffer},
   {"_getRtkStatus", "(Lgpsplus/rtklib/RtkControlResult;)V", (void*)RtkServer__get_rtk_status},
   {"_getObservationStatus", "(ILgpsplus/rtklib/RtkServerObservationStatus$Native;)V", (void*)RtkServer__get_observation_status},
   {"_writeCommands", "([Ljava/lang/String;)V", (void*)RtkServer__write_commands},
   {"_readsp3","(Ljava/lang/String;)V", (void*)RtkServer__readsp3},
   {"_readsatant","(Ljava/lang/String;)V", (void*)RtkServer__readsatant}
};

static int init_observation_status_fields(JNIEnv* env) {
    jclass clazz = (*env)->FindClass(env, "gpsplus/rtklib/RtkServerObservationStatus$Native");

    if (clazz == NULL)
       return JNI_FALSE;

    obs_status_fields.ns = (*env)->GetFieldID(env, clazz, "ns", "I");
    obs_status_fields.time = (*env)->GetFieldID(env, clazz, "time", "Lgpsplus/rtklib/GTime;");
    obs_status_fields.sat = (*env)->GetFieldID(env, clazz, "sat", "[I");
    obs_status_fields.az = (*env)->GetFieldID(env, clazz, "az", "[D");
    obs_status_fields.el = (*env)->GetFieldID(env, clazz, "el", "[D");
    obs_status_fields.freq1_snr = (*env)->GetFieldID(env, clazz, "freq1Snr", "[I");
    obs_status_fields.freq2_snr = (*env)->GetFieldID(env, clazz, "freq2Snr", "[I");
    obs_status_fields.freq3_snr = (*env)->GetFieldID(env, clazz, "freq3Snr", "[I");
    obs_status_fields.vsat = (*env)->GetFieldID(env, clazz, "vsat", "[I");

    if ((obs_status_fields.ns == NULL)
	  || (obs_status_fields.time == NULL)
	  || (obs_status_fields.sat == NULL)
	  || (obs_status_fields.az == NULL)
	  || (obs_status_fields.el == NULL)
	  || (obs_status_fields.freq1_snr == NULL)
	  || (obs_status_fields.freq2_snr == NULL)
	  || (obs_status_fields.freq3_snr == NULL)
	  || (obs_status_fields.vsat == NULL)) {
       LOGV("gpsplus/rtklib/RtkServerObservationStatus fields not found:%s%s%s%s%s%s%s%s%s",
	     (obs_status_fields.ns == NULL ? " ns" : ""),
	     (obs_status_fields.time == NULL ? " time" : ""),
	     (obs_status_fields.sat == NULL ? " sat" : ""),
	     (obs_status_fields.az == NULL ? " az" : ""),
	     (obs_status_fields.el == NULL ? " el" : ""),
	     (obs_status_fields.freq1_snr == NULL ? " freq1_snr " : ""),
	     (obs_status_fields.freq2_snr == NULL ? " freq2_snr " : ""),
	     (obs_status_fields.freq3_snr == NULL ? " freq3_snr " : ""),
	     (obs_status_fields.vsat == NULL ? " vsat" : "")
	     );
       return JNI_FALSE;
    }

    return JNI_TRUE;
}

int registerRtkServerNatives(JNIEnv* env) {
    /* look up the class */
    jclass clazz = (*env)->FindClass(env, "gpsplus/rtklib/RtkServer");

    if (clazz == NULL)
       return JNI_FALSE;

    if ((*env)->RegisterNatives(env, clazz, nativeMethods, sizeof(nativeMethods)
	     / sizeof(nativeMethods[0])) != JNI_OK)
       return JNI_FALSE;

    m_object_field = (*env)->GetFieldID(env, clazz, "mObject", "J");
    if (m_object_field == NULL)
       return JNI_FALSE;

    if ( ! init_observation_status_fields(env))
       return JNI_FALSE;

    return JNI_TRUE;
}

