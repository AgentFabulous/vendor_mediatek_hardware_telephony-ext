package org.lineageos.mediatek.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.lang.StringBuffer;

public class MtkTftStatus implements Parcelable {
    public static final int OPCODE_SPARE = 0;
    public static final int OPCODE_CREATE_NEW_TFT = 1;
    public static final int OPCODE_DELETE_TFT = 2;
    public static final int OPCODE_ADD_PF = 3;
    public static final int OPCODE_REPLACE_PF = 4;
    public static final int OPCODE_DELETE_PF = 5;
    public static final int OPCODE_NOTFT_OP = 6;
    public static final int OPCODE_RESERVED = 7;
    public int mOperation = -1;
    public ArrayList<MtkPacketFilterInfo> mMtkPacketFilterInfoList;
    public MtkTftParameter mMtkTftParameter;

    public MtkTftStatus (int operation, ArrayList<MtkPacketFilterInfo> mtkPacketFilterInfo,
                             MtkTftParameter mtkTftParameter) {
        mOperation = operation;
        mMtkPacketFilterInfoList = mtkPacketFilterInfo;
        mMtkTftParameter = mtkTftParameter;
    }

    public static MtkTftStatus readFrom(Parcel p) {
        int operation = p.readInt();
        int pfNumber = p.readInt();
        ArrayList<MtkPacketFilterInfo> pfList = new ArrayList<MtkPacketFilterInfo>();
        for (int i = 0; i < pfNumber; i++) {
            MtkPacketFilterInfo pfInfo = MtkPacketFilterInfo.readFrom(p);
            pfList.add(pfInfo);
        }
        MtkTftParameter tftParameter = MtkTftParameter.readFrom(p);
        return new MtkTftStatus(operation, pfList, tftParameter);
    }

    public void writeTo(Parcel p) {
        p.writeInt(mOperation);
        p.writeInt(mMtkPacketFilterInfoList.size());
        for (MtkPacketFilterInfo pfInfo : mMtkPacketFilterInfoList)
            pfInfo.writeTo(p);

        mMtkTftParameter.writeTo(p);
    }

    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer("operation=" + mOperation + " [PacketFilterInfo");
        for (MtkPacketFilterInfo pfInfo : mMtkPacketFilterInfoList)
            buf.append(pfInfo.toString());

        buf.append("], TftParameter[" + mMtkTftParameter + "]]");
        return buf.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeTo(dest);
    }

    public static final Parcelable.Creator<MtkTftStatus> CREATOR =
            new Parcelable.Creator<MtkTftStatus>() {
        @Override
        public MtkTftStatus createFromParcel(Parcel source) {
            MtkTftStatus tftStatus = MtkTftStatus.readFrom(source);
            return tftStatus;
        }

        @Override
        public MtkTftStatus[] newArray(int size) {
            return new MtkTftStatus[size];
        }
    };
}
