package cn.leancloud.flyme;

import android.Manifest;
import android.content.Context;

import cn.leancloud.AVException;
import cn.leancloud.AVFlymePushMessageReceiver;
import cn.leancloud.AVInstallation;
import cn.leancloud.AVLogger;
import cn.leancloud.AVManifestUtils;
import cn.leancloud.callback.SaveCallback;
import cn.leancloud.convertor.ObserverBuilder;
import cn.leancloud.utils.LogUtil;
import cn.leancloud.utils.StringUtil;

/**
 * Created by wli on 16/6/27.
 */
public class AVMixPushManager {
  private static final AVLogger LOGGER = LogUtil.getLogger(AVMixPushManager.class);

  public static final String MIXPUSH_PROFILE = "deviceProfile";

  /**
   * 魅族推送的 deviceProfile
   */
  public static String flymeDeviceProfile = "";
  public static int flymeMStatusBarIcon = 0;
  static Class flymePushReceiverClazz = AVFlymePushMessageReceiver.class;

  /**
   * 注册魅族推送
   * @param context 上下文
   * @param flymeId flyme app id
   * @param flymeKey flyme app key
   * @param profile 魅族推送配置
   * @return true - register succeed
   *         false - register failed
   */
  public static boolean registerFlymePush(Context context, String flymeId, String flymeKey,
                                          String profile) {
    return registerFlymePush(context, flymeId, flymeKey, profile, null);
  }

  /**
   * 注册魅族推送
   * @param context 上下文
   * @param flymeId flyme app id
   * @param flymeKey flyme app key
   * @param profile 魅族推送配置
   * @param customizedReceiver 自定义 receiver
   * @return true - register succeed
   *         false - register failed
   */
  public static boolean registerFlymePush(Context context, String flymeId, String flymeKey,
                                          String profile, Class customizedReceiver) {
    if (null == context) {
      printErrorLog("register error, context is null!");
      return false;
    }
    boolean result = false;
    if (!com.meizu.cloud.pushsdk.util.MzSystemUtils.isBrandMeizu(context)) {
      printErrorLog("register error, is not flyme phone!");
    } else {
      if (null != customizedReceiver) {
        flymePushReceiverClazz = customizedReceiver;
      }
      if (!checkFlymeManifest(context)) {
        printErrorLog("register error, mainifest is incomplete!");
      } else {
        flymeDeviceProfile = profile;
        com.meizu.cloud.pushsdk.PushManager.register(context, flymeId, flymeKey);
        result = true;
        LOGGER.d("start register flyme push");
      }
    }
    return result;
  }

  /**
   * 注册魅族推送
   * @param context 上下文
   * @param flymeId flyme app id
   * @param flymeKey flyme app key
   * @return true - register succeed
   *         false - register failed
   */
  public static boolean registerFlymePush(Context context, String flymeId, String flymeKey) {
    return registerFlymePush(context, flymeId, flymeKey, "", null);
  }

  /**
   * 注册魅族推送
   * @param context 上下文
   * @param flymeId flyme app id
   * @param flymeKey flyme app key
   * @param customizedReceiver 自定义 receiver
   * @return true - register succeed
   *         false - register failed
   */
  public static boolean registerFlymePush(Context context, String flymeId, String flymeKey,
                                          Class customizedReceiver) {
    return registerFlymePush(context, flymeId, flymeKey, "", customizedReceiver);
  }

  /**
   * set flyme MStatus bar icon.
   *
   * @param icon icon resource id.
   */
  public static void setFlymeMStatusbarIcon(int icon) {
    flymeMStatusBarIcon = icon;
  }

  /**
   * 取消混合推送的注册
   * 取消成功后，消息会通过 LeanCloud websocket 发送
   */
  public static void unRegisterMixPush() {
    AVInstallation installation = AVInstallation.getCurrentInstallation();
    String vendor = installation.getString(AVInstallation.VENDOR);
    if (!StringUtil.isEmpty(vendor)) {
      installation.put(AVInstallation.VENDOR, "lc");
      installation.saveInBackground().subscribe(ObserverBuilder.buildSingleObserver(new SaveCallback() {
        @Override
        public void done(AVException e) {
          if (null != e) {
            printErrorLog("unRegisterMixPush error!");
          } else {
            LOGGER.d("Registration canceled successfully!");
          }
        }
      }));
    }
  }

  private static boolean checkFlymeManifest(Context context) {
    boolean result = false;
    try {
      result = AVManifestUtils.checkPermission(context, android.Manifest.permission.INTERNET)
          && AVManifestUtils.checkPermission(context, android.Manifest.permission.READ_PHONE_STATE)
          && AVManifestUtils.checkPermission(context, android.Manifest.permission.ACCESS_NETWORK_STATE)
          && AVManifestUtils.checkPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
          && AVManifestUtils.checkReceiver(context, flymePushReceiverClazz);
    } catch (Exception e) {
      LOGGER.d(e.getMessage());
    }
    return result;
  }

  private static void printErrorLog(String error) {
    if (!StringUtil.isEmpty(error)) {
      LOGGER.e(error);
    }
  }
}
