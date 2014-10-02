package api.process;

import java.util.Calendar;
import java.util.Vector;

import uti.utility.MyConfig;
import uti.utility.MyConfig.ChannelType;
import uti.utility.VNPApplication;
import uti.utility.MyConvert;
import uti.utility.MyLogger;
import api.process.Charge.ErrorCode;
import dat.history.MOLog;
import dat.sub.Subscriber;
import dat.sub.Subscriber.Status;
import dat.sub.SubscriberObject;
import dat.sub.UnSubscriber;
import db.define.MyTableModel;

public class ProDeregisterAll
{

	public enum DeregAllResult
	{
		// 0 Đăng ký thành công dịch vụ
		Success(0),
		// 1 Thuê bao này đã tồn tại
		NotExistSub(1),
		// 1xx Đều là đăng ký không thành công
		Fail(100),
		// Lỗi hệ thống
		SystemError(101),
		// Thông tin nhập vào không hợp lệ
		InputInvalid(102), ;

		private int value;

		private DeregAllResult(int value)
		{
			this.value = value;
		}

		public Integer GetValue()
		{
			return this.value;
		}

		public static DeregAllResult FromInt(int iValue)
		{
			for (DeregAllResult type : DeregAllResult.values())
			{
				if (type.GetValue() == iValue)
					return type;
			}
			return Fail;
		}
	}

	String MSISDN = "";
	String RequestID = "";
	String Channel = "";
	String Keyword = "HUY";
	String AppName = "";
	String UserName = "";
	String IP = "";

	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath, this.getClass().toString());

	Calendar mCal_Current = Calendar.getInstance();

	Vector<SubscriberObject> mList = new Vector<SubscriberObject>();

	Subscriber mSub = null;
	UnSubscriber mUnSub = null;
	MOLog mMOLog = null;

	MyTableModel mTable_MOLog = null;
	MyTableModel mTable_Sub = null;
	MyTableModel mTable_UnSub = null;

	Charge mCharge = new Charge();

	DeregAllResult mDeregAllResult = DeregAllResult.Fail;

	String MTContent = "";
	MyTableModel mTableLog = null;

	MyConfig.ChannelType mChannel = ChannelType.NOTHING;
	VNPApplication mVNPApp = new VNPApplication();

	Integer PID = 0;

	public ProDeregisterAll(String MSISDN, String RequestID, String Channel, String AppName, String UserName, String IP) throws Exception
	{
		this.MSISDN = MSISDN;
		this.RequestID = RequestID;
		this.Channel = Channel.toUpperCase().trim();
		this.AppName = AppName;
		this.UserName = UserName;
		this.IP = IP;

		this.mChannel = Common.GetChannelType(Channel);
		this.mVNPApp = Common.GetApplication(AppName);
		this.PID = MyConvert.GetPIDByMSISDN(MSISDN, LocalConfig.MAX_PID);
	}

	private void Init() throws Exception
	{
		try
		{
			mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
			mUnSub = new UnSubscriber(LocalConfig.mDBConfig_MSSQL);
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = CurrentData.GetTable_MOLog();
			mTable_Sub = CurrentData.GetTable_Sub();
			mTable_UnSub = CurrentData.GetTable_UnSub();
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean UpdateUnsub(SubscriberObject mSubObj) throws Exception
	{
		try
		{
			mTable_UnSub.Clear();
			mTable_UnSub = mSubObj.AddNewRow(mTable_UnSub);

			if (!mUnSub.Update(0, mTable_UnSub.GetXML()))
			{
				mLog.log.info(" Update tinh trang Huy Thue Bao cho Table UnSub KHONG THANH CONG: XML Update-->" + mTable_UnSub.GetXML());
				return false;
			}

			return true;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean MoveToSub(SubscriberObject mSubObj) throws Exception
	{
		try
		{
			mTable_UnSub.Clear();
			mTable_UnSub = mSubObj.AddNewRow(mTable_UnSub);

			if (!mUnSub.Move(0, mTable_UnSub.GetXML()))
			{
				mLog.log.info(" Move Tu Sub Sang UnSub KHONG THANH CONG: XML Insert-->" + mTable_UnSub.GetXML());
				return false;
			}

			return true;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	protected void CreateUnSub(SubscriberObject mSubObj) throws Exception
	{

		mSubObj.mChannelType = mChannel;
		mSubObj.mStatus = Status.UndoSub;

		// Chỉ khi nào đang đăng ký thì mới thêm ngày hủy
		// nếu đang hủy rồi thì thôi
		if (!mSubObj.IsDereg)
			mSubObj.DeregDate = mCal_Current.getTime();

		mSubObj.mVNPApp = mVNPApp;
		mSubObj.UserName = UserName;
		mSubObj.IP = IP;

	}

	public String Process()
	{
		mDeregAllResult = DeregAllResult.Fail;
		String ListFail = "";
		try
		{
			// Khoi tao
			Init();

			// Lấy thông tin khách hàng đã đăng ký
			MyTableModel mTable_Sub = mSub.Select(2, PID.toString(), MSISDN);
			mList = SubscriberObject.ConvertToList(mTable_Sub, false);

			MyTableModel mTable_UnSub = mUnSub.Select(2, PID.toString(), MSISDN);
			Vector<SubscriberObject> mListUnSub = SubscriberObject.ConvertToList(mTable_UnSub, true);

			for (SubscriberObject mSubObj : mListUnSub)
			{
				if (mSubObj.mStatus == Status.UndoSub)
					continue;

				mList.add(mSubObj);
			}

			if (mList.size() < 1)
			{
				mDeregAllResult = DeregAllResult.NotExistSub;
				return GetResponse(mDeregAllResult);
			}

			ListFail = "MSISDN:" + MSISDN;
			for (SubscriberObject mSubObj : mList)
			{

				CreateUnSub(mSubObj);

				if (mSubObj.IsDereg)
				{
					if (!UpdateUnsub(mSubObj))
					{
						ListFail += "|Update tinh trang Huy Thue Bao cho table Unsub khong thanh cong";
					}
				}
				else
				{
					ErrorCode mResult = mCharge.ChargeDereg(mSubObj, mSubObj.mChannelType, Keyword);
					if (mResult != ErrorCode.ChargeSuccess)
					{
						ListFail += "|ChargeDereg khong thanh cong ErrorCode:" + mResult.toString();
					}
					if (!MoveToSub(mSubObj))
					{
						ListFail += "|MoveToSub khong thanh cong";
					}
				}
			}

			mDeregAllResult = DeregAllResult.Success;

		}
		catch (Exception ex)
		{
			mDeregAllResult = DeregAllResult.SystemError;
			mLog.log.error(ex);
		}
		finally
		{
			MyLogger.WriteDataLog(LocalConfig.LogDataFolder, "_DeregsterAll_FAIL", "INFO --> " + ListFail);
		}
		return GetResponse(mDeregAllResult);
	}

	private String GetResponse(DeregAllResult mDeregAllResult)
	{
		String XMLReturn = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" + "<RESPONSE>" + "<ERRORID>" + mDeregAllResult.GetValue() + "</ERRORID>"
				+ "<ERRORDESC>" + mDeregAllResult.toString() + "</ERRORDESC>" + "</RESPONSE>";
		return XMLReturn;
	}

}