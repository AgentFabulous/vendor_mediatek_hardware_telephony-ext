package org.lineageos.mediatek.telephony;

import android.content.Context;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.HwBinder;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.WorkSource;
import android.telephony.Rlog;
import android.telephony.data.DataProfile;

import android.hardware.radio.V1_0.IRadio;

import com.android.internal.telephony.RadioIndication;
import com.android.internal.telephony.RadioResponse;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.RILRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import vendor.mediatek.hardware.radio.V3_0.*;

public class MtkRIL extends RIL {
    private static final String LOG_TAG = "MtkRIL";
    // Should be kept in sync with RIL.HIDL_SERVICE_NAME
    static final String[] HIDL_SERVICE_NAME = {"slot1", "slot2", "slot3"};

    // ++ Mtk Radio 2.0
    vendor.mediatek.hardware.radio.V2_0.IRadioIndication mMtkRadioIndication2;
    vendor.mediatek.hardware.radio.V2_0.IRadioResponse mMtkRadioResponse2;
    volatile vendor.mediatek.hardware.radio.V2_0.IRadio mMtkRadioProxy2 = null;
    // -- Mtk Radio 2.0
    // ++ Mtk Radio 3.0
    vendor.mediatek.hardware.radio.V3_0.IRadioIndication mMtkRadioIndication3;
    vendor.mediatek.hardware.radio.V3_0.IRadioResponse mMtkRadioResponse3;
    volatile vendor.mediatek.hardware.radio.V3_0.IRadio mMtkRadioProxy3 = null;
    // -- Mtk Radio 3.0
    final AtomicLong mMtkRadioProxyCookie = new AtomicLong(0);
    final Handler mMtkRilHandler = new MtkRilHandler();
    final MtkRadioProxyDeathRecipient mMtkRadioProxyDeathRecipient = new MtkRadioProxyDeathRecipient();

    private Integer mMtkPhoneId;

    public MtkRIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public MtkRIL(Context context, int preferredNetworkType,
            int cdmaSubscription, Integer instanceId) {
        super(context, preferredNetworkType, cdmaSubscription, instanceId);
        mMtkPhoneId = instanceId == null ? 0 : instanceId;
        ensureMtkRadioProxy(); // Make sure our MTK RadioProxy is loaded
        riljLogMtk("MTK RIL extension loaded");
    }

    @Override
    public synchronized IRadio getRadioProxy(Message result) {
        IRadio radioProxy = super.getRadioProxy(result);
        if (radioProxy != null) {
            // Make sure we always connect to Mtk RadioProxy
            // The first call to this function is made in super(),
            // during which we haven't initialized yet. Corresponding
            // checks are placed in getRadioProxyMtk()
            ensureMtkRadioProxy();
        }
        return radioProxy;
    }

    private synchronized void ensureMtkRadioProxy() {
        if (getRadioProxyMtk3() == null) {
            getRadioProxyMtk2();
        }
    }

    private synchronized vendor.mediatek.hardware.radio.V2_0.IRadio getRadioProxyMtk2() {
        if (mMtkRadioProxy2 == null && mMtkPhoneId != null && mMtkRadioProxyCookie != null) {
            try {
                mMtkRadioProxy2 =
                    vendor.mediatek.hardware.radio.V2_0.IRadio.getService(
                        HIDL_SERVICE_NAME[mMtkPhoneId]);
                if (mMtkRadioProxy2 != null) {
                    mMtkRadioProxy2.linkToDeath(mMtkRadioProxyDeathRecipient,
                        mMtkRadioProxyCookie.incrementAndGet());
                    if (mMtkRadioResponse2 == null && mMtkRadioIndication2 == null) {
                        mMtkRadioResponse2 = new MtkRadioResponse2(this, getRadioResponse());
                        mMtkRadioIndication2 = new MtkRadioIndication2(this, getRadioIndication());
                    }
                    mMtkRadioProxy2.setResponseFunctionsMtk(mMtkRadioResponse2, mMtkRadioIndication2);
                    return mMtkRadioProxy2;
                }
            } catch (RemoteException | NoSuchElementException e) {
                riljLogMtk("MTK RadioProxy 2.0 is not available");
                return null;
            }
        }
        return mMtkRadioProxy2;
    }

    private synchronized vendor.mediatek.hardware.radio.V3_0.IRadio getRadioProxyMtk3() {
        if (mMtkRadioProxy3 == null && mMtkPhoneId != null && mMtkRadioProxyCookie != null) {
            try {
                mMtkRadioProxy3 =
                    vendor.mediatek.hardware.radio.V3_0.IRadio.getService(
                        HIDL_SERVICE_NAME[mMtkPhoneId]);
                if (mMtkRadioProxy3 != null) {
                    mMtkRadioProxy3.linkToDeath(mMtkRadioProxyDeathRecipient,
                        mMtkRadioProxyCookie.incrementAndGet());
                    if (mMtkRadioResponse3 == null && mMtkRadioIndication3 == null) {
                        mMtkRadioResponse3 = new MtkRadioResponse3(this, getRadioResponse());
                        mMtkRadioIndication3 = new MtkRadioIndication3(this, getRadioIndication());
                    }
                    mMtkRadioProxy3.setResponseFunctionsMtk(mMtkRadioResponse3, mMtkRadioIndication3);
                    return mMtkRadioProxy3;
                }
            } catch (RemoteException | NoSuchElementException e) {
                riljLogMtk("MTK RadioProxy 3.0 is not available");
                return null;
            }
        }
        return mMtkRadioProxy3;
    }

    @Override
    public void setupDataCall(int accessNetworkType, DataProfile dataProfile, boolean isRoaming,
                              boolean allowRoaming, int reason, LinkProperties linkProperties,
                              Message result) {
        riljLogMtk("Forcing setDataAllowed() before setupDataCall()");
        setDataAllowed(true, null);
        super.setupDataCall(accessNetworkType, dataProfile, isRoaming, allowRoaming, reason,
            linkProperties, result);
    }

    void resetRadioProxyMtk() {
        riljLogMtk("MTK RadioProxy died; retrying");
        mMtkRadioProxy2 = null;
        mMtkRadioProxy3 = null;
        mMtkRadioProxyCookie.incrementAndGet();
        ensureMtkRadioProxy();
    }

    void riljLogMtk(String msg) {
        Rlog.d(LOG_TAG, msg + (" [SUB" + mMtkPhoneId + "]"));
    }

    void setCallIndication(int callId, int seqNo) {
        // Ensure that mMtkRadioProxy is updated.
        vendor.mediatek.hardware.radio.V3_0.IRadio radioProxy3 = getRadioProxyMtk3();
        vendor.mediatek.hardware.radio.V2_0.IRadio radioProxy2 = getRadioProxyMtk2();
        RILRequest rr = null;
        if (radioProxy2 != null || radioProxy3 != null) {
            rr = obtainRequestMtk(2016 /* RIL_REQUEST_SET_CALL_INDICATION */, null, mRILDefaultWorkSource);
            riljLogMtk(rr.getSerial() + "> " + "setCallIndication");
        }

        if (radioProxy3 != null) {
            try {
                radioProxy3.setCallIndication(
                    rr.getSerial(), 0 /* 0: allowed, 1: disallowed */, callId, seqNo);
            } catch (RemoteException | RuntimeException e) {
                riljLogMtk("failed to call setCallIndication");
            }
        } else if (radioProxy2 != null) {
            try {
                radioProxy2.setCallIndication(
                    rr.getSerial(), 0 /* 0: allowed, 1: disallowed */, callId, seqNo);
            } catch (RemoteException | RuntimeException e) {
                riljLogMtk("failed to call setCallIndication");
            }
        }
    }

    // WARNING: VERY DIRTY CODE AHEAD
    private RadioIndication getRadioIndication() {
        try {
            Field f = RIL.class.getDeclaredField("mRadioIndication");
            f.setAccessible(true);
            return (RadioIndication) f.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private RadioResponse getRadioResponse() {
        try {
            Field f = RIL.class.getDeclaredField("mRadioResponse");
            f.setAccessible(true);
            return (RadioResponse) f.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private RILRequest obtainRequestMtk(int request, Message result, WorkSource workSource) {
        try {
            Method m = RIL.class.getDeclaredMethod("obtainRequest", int.class, Message.class, WorkSource.class);
            m.setAccessible(true);
            return (RILRequest) m.invoke(this, request, result, workSource);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void processIndicationMtk(int indicationType) {
        try {
            Method m = RIL.class.getDeclaredMethod("processIndication", int.class);
            m.setAccessible(true);
            m.invoke(this, indicationType);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected RegistrantList mDedicatedBearerActivedRegistrants = new RegistrantList();
    public void registerForDedicatedBearerActivated(Handler h, int what, Object obj) {
        mDedicatedBearerActivedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForDedicatedBearerActivated(Handler h) {
        mDedicatedBearerActivedRegistrants.remove(h);
    }

    protected RegistrantList mDedicatedBearerModifiedRegistrants = new RegistrantList();
    public void registerForDedicatedBearerModified(Handler h, int what, Object obj) {
        mDedicatedBearerModifiedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForDedicatedBearerModified(Handler h) {
        mDedicatedBearerModifiedRegistrants.remove(h);
    }

    protected RegistrantList mDedicatedBearerDeactivatedRegistrants = new RegistrantList();
    public void registerForDedicatedBearerDeactivationed(Handler h, int what, Object obj) {
        mDedicatedBearerDeactivatedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForDedicatedBearerDeactivationed(Handler h) {
        mDedicatedBearerDeactivatedRegistrants.remove(h);
    }

    public MtkDedicateDataCallResponse convertDedicatedDataCallResult(DedicateDataCall ddcResult) {
        int ddcId = ddcResult.ddcId;
        int interfaceId = ddcResult.interfaceId;
        int primaryCid = ddcResult.primaryCid;
        int cid = ddcResult.cid;
        int active = ddcResult.active;
        int signalingFlag = ddcResult.signalingFlag;
        int bearerId = ddcResult.bearerId;
        int failCause = ddcResult.failCause;

        MtkQosStatus mtkQosStatus = null;
        riljLogMtk("ddcResult.hasQos: " + ddcResult.hasQos);
        if (ddcResult.hasQos != 0) {
            int qci = ddcResult.qos.qci;
            int dlGbr = ddcResult.qos.dlGbr;
            int ulGbr = ddcResult.qos.ulGbr;
            int dlMbr = ddcResult.qos.dlMbr;
            int ulMbr = ddcResult.qos.ulMbr;
            mtkQosStatus = new MtkQosStatus(qci, dlGbr, ulGbr, dlMbr, ulMbr);
        }

        MtkTftStatus mtkTftStatus = null;
        riljLogMtk("ddcResult.hasTft: " + ddcResult.hasTft);
        if (ddcResult.hasTft != 0) {
            int operation = ddcResult.tft.operation;
            ArrayList<MtkPacketFilterInfo> mtkPacketFilterInfo
                    = new ArrayList<MtkPacketFilterInfo>();
            for(PktFilter info : ddcResult.tft.pfList){
                MtkPacketFilterInfo pfInfo
                        = new MtkPacketFilterInfo(info.id, info.precedence, info.direction,
                                                  info.networkPfIdentifier, info.bitmap,
                                                  info.address, info.mask,
                                                  info.protocolNextHeader, info.localPortLow,
                                                  info.localPortHigh, info.remotePortLow,
                                                  info.remotePortHigh, info.spi, info.tos,
                                                  info.tosMask, info.flowLabel);
                mtkPacketFilterInfo.add(pfInfo);
            }

            ArrayList<Integer> pfList = (ArrayList<Integer>)ddcResult.tft.tftParameter.linkedPfList;
            MtkTftParameter mtkTftParameter = new MtkTftParameter(pfList);
            mtkTftStatus = new MtkTftStatus(operation, mtkPacketFilterInfo, mtkTftParameter);
        }

        String pcscfAddress = ddcResult.pcscf;
        return new MtkDedicateDataCallResponse(interfaceId, primaryCid, cid, active,
                                               signalingFlag, bearerId, failCause,
                                               mtkQosStatus, mtkTftStatus, pcscfAddress);
    }

    final class MtkRadioProxyDeathRecipient implements HwBinder.DeathRecipient {
        @Override
        public void serviceDied(long cookie) {
            // Deal with service going away
            riljLogMtk("serviceDied");
            mMtkRilHandler.sendMessage(mMtkRilHandler.obtainMessage(0, cookie));
        }
    }

    final class MtkRilHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    // Mtk RadioProxy died
                    if ((long) msg.obj == mMtkRadioProxyCookie.get()) {
                        resetRadioProxyMtk();
                    }
            }
        }
    }
}
