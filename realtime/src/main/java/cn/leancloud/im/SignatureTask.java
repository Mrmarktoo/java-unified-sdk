package cn.leancloud.im;

class SignatureTask implements Runnable {
  private final SignatureCallback callback;
  private final String clientId;
  public SignatureTask(SignatureCallback callback, String clientId) {
    this.callback = callback;
    this.clientId = clientId;
  }
  public void run() {
    if (null == this.callback) {
      return;
    }
    try {
      Signature signature;
      if (callback.useSignatureCache()) {
        signature = AVSessionCacheHelper.SignatureCache.getSessionSignature(this.clientId);
        if (null != signature && !signature.isExpired()) {
          ;
        } else {
          signature = this.callback.computeSignature();
        }
      } else {
        signature = this.callback.computeSignature();
      }
      this.callback.onSignatureReady(signature, null);
      if (callback.cacheSignature()) {
        AVSessionCacheHelper.SignatureCache.addSessionSignature(this.clientId, signature);
      }
    } catch (SignatureFactory.SignatureException ex) {
      this.callback.onSignatureReady(null, ex);
    }
  }
  public void start() {
    BackgroundThreadpool.getInstance().execute(this);
  }
}
