package api.process;

import java.util.Calendar;

import uti.utility.MyConfig;
import uti.utility.MyConfig.ChannelType;
import uti.utility.VNPApplication;
import uti.utility.MyConvert;
import uti.utility.MyLogger;
import api.process.Charge.ErrorCode;
import api.process.PromotionObject.AdvertiseType;
import api.process.PromotionObject.BundleType;
import api.process.PromotionObject.PromotionType;
import api.process.PromotionObject.TrialType;
import dat.content.DefineMT;
import dat.content.DefineMT.MTType;
import dat.content.Keyword;
import dat.history.MOLog;
import dat.history.MOObject;
import dat.history.WapRegLog;
import dat.sub.Subscriber;
import dat.sub.Subscriber.Status;
import dat.sub.SubscriberObject;
import dat.sub.SubscriberObject.InitType;
import dat.sub.UnSubscriber;
import db.define.MyTableModel;

public class ProRegister
{
	public enum RegResult
	{
		// 0 Đăng ký thành công dịch vụ
		Success(0),
		// 1 Thuê bao này đã tồn tại
		ExistSub(1),
		// 2 Đăng ký rồi và đăng ký lại dịch vụ
		Repeat(2),
		// 3 Đăng ký thành công dịch vụ và không bị trừ cước đăng ký
		SuccessFree(3),
		// 4 Đăng ký thành công dịch vụ và bị trừ cước đăng ký
		SucessPay(4),
		// 5 Đăng ký không thành công do không đủ tiền trong tài khoản
		EnoughMoney(5),
		// 1xx Đều là đăng ký không thành công
		Fail(100),
		// Lỗi hệ thống
		SystemError(101),
		// Thông tin nhập vào không hợp lệ
		InputInvalid(102), ;

		private int value;

		private RegResult(int value)
		{
			this.value = value;
		}

		public Integer GetValue()
		{
			return this.value;
		}

		public static RegResult FromInt(int iValue)
		{
			for (RegResult type : RegResult.values())
			{
				if (type.GetValue() == iValue)
					return type;
			}
			return Fail;
		}
	}

	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath, this.getClass().toString());

	String MSISDN = "";
	String RequestID = "";
	String PackageName = "";
	String Channel = "";

	String Keyword = "DK API";
	String AppName = "";
	String UserName = "";
	String IP = "";

	String Promotion = "";
	String Trial = "";
	String Note = "";
	String Bundle = "";

	PromotionObject mPromoObj = new PromotionObject();
	SubscriberObject mSubObj = new SubscriberObject();

	Calendar mCal_Current = Calendar.getInstance();
	Calendar mCal_Expire = Calendar.getInstance();

	Subscriber mSub = null;
	UnSubscriber mUnSub = null;
	MOLog mMOLog = null;
	Keyword mKeyword = null;

	MyTableModel mTable_MOLog = null;
	MyTableModel mTable_Sub = null;
	MyTableModel mTable_WapRegLog = null;

	Charge mCharge = new Charge();
	DefineMT.MTType mMTType = MTType.RegFail;

	String MTContent = "";

	// Thời gian miễn phí để chèn vào MT trả về cho khách hàng
	String FreeTime = "ngay dau tien";

	MyConfig.ChannelType mChannel = ChannelType.NOTHING;
	VNPApplication mVNPApp = new VNPApplication();

	Integer PID = 0;

	// Nếu > 0 thì ID của partner cần pass
	int PartnerID_Pass = 0;

	public ProRegister(String MSISDN, String RequestID, String PackageName, String Promotion, String Trial,
			String Bundle, String Note, String Channel, String AppName, String UserName, String IP) throws Exception
	{
		this.MSISDN = MSISDN.trim();
		this.RequestID = RequestID.trim();
		this.PackageName = PackageName.trim();

		this.Promotion = Promotion.trim();
		this.Trial = Trial.trim();
		this.Bundle = Bundle.trim();
		this.Note = Note.trim();

		this.Channel = Channel.toUpperCase().trim();
		this.AppName = AppName.trim();
		this.UserName = UserName.trim();
		this.IP = IP.trim();

		this.mChannel = Common.GetChannelType(Channel);
		this.mVNPApp = Common.GetApplication(AppName);
		this.PID = MyConvert.GetPIDByMSISDN(MSISDN, LocalConfig.MAX_PID);
	}

	/**
	 * Tính toán trường hợp khuyến mãi
	 * 
	 * @throws Exception
	 */
	private void CalculatePromotion() throws Exception
	{
		try
		{
			if (Bundle.equalsIgnoreCase("1"))
			{
				mPromoObj.mAdvertiseType = AdvertiseType.Bundle;
				mPromoObj.mBundleType = BundleType.NeverExpire;
			}
			else if (!Trial.equalsIgnoreCase("") && !Trial.equalsIgnoreCase("0"))
			{
				if (Trial.indexOf("c") > 0)
				{
					mPromoObj.mTrialType = TrialType.Day;
				}
				else if (Trial.indexOf("d") > 0)
				{
					mPromoObj.mTrialType = TrialType.Day;
				}
				else if (Trial.indexOf("w") > 0)
				{
					mPromoObj.mTrialType = TrialType.Week;
				}
				else if (Trial.indexOf("m") > 0)
				{
					mPromoObj.mTrialType = TrialType.Month;
				}

				mPromoObj.TrialNumberFree = Integer.parseInt(Trial.substring(0, Trial.length() - 1));
				mPromoObj.mAdvertiseType = AdvertiseType.Trial;
			}
			else if (!Promotion.equalsIgnoreCase("") && !Promotion.equalsIgnoreCase("0"))
			{
				if (Promotion.indexOf("c") > 0)
				{
					mPromoObj.mPromotionType = PromotionType.Day;
				}
				else if (Promotion.indexOf("d") > 0)
				{
					mPromoObj.mPromotionType = PromotionType.Day;
				}
				else if (Promotion.indexOf("w") > 0)
				{
					mPromoObj.mPromotionType = PromotionType.Week;
				}
				else if (Promotion.indexOf("m") > 0)
				{
					mPromoObj.mPromotionType = PromotionType.Month;
				}

				mPromoObj.PromotionNumberFree = Integer.parseInt(Promotion.substring(0, Promotion.length() - 1));
				mPromoObj.mAdvertiseType = AdvertiseType.Promotion;
			}

		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	/**
	 * Thiết lập thông tin Promotion cho đối tượng sub
	 * 
	 * @throws Exception
	 */
	private void SetPromotionToSub() throws Exception
	{
		mCal_Expire = Calendar.getInstance();
		mCal_Expire.set(Calendar.MILLISECOND, 0);
		mCal_Expire.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
				mCal_Current.get(Calendar.DATE), 23, 59, 59);
		if (mPromoObj.mAdvertiseType == AdvertiseType.Bundle)
		{
			if (mPromoObj.mBundleType == BundleType.NeverExpire)
			{
				mCal_Expire.set(2030, mCal_Current.get(Calendar.MONTH), mCal_Current.get(Calendar.DATE), 23, 59, 59);
			}
			mSubObj.mStatus = Subscriber.Status.ActiveBundle;
		}
		else if (mPromoObj.mAdvertiseType == AdvertiseType.Trial)
		{
			if (mPromoObj.mTrialType == TrialType.Day)
			{
				mCal_Expire.add(Calendar.DATE, mPromoObj.TrialNumberFree - 1);
				FreeTime = "" + mPromoObj.TrialNumberFree + " ngay";
			}
			else if (mPromoObj.mTrialType == TrialType.Week)
			{
				mCal_Expire.add(Calendar.DATE, mPromoObj.TrialNumberFree * 7);
				FreeTime = "" + mPromoObj.TrialNumberFree + " tuan";
			}
			else if (mPromoObj.mTrialType == TrialType.Month)
			{
				mCal_Expire.add(Calendar.MONTH, mPromoObj.TrialNumberFree);
				FreeTime = "" + mPromoObj.TrialNumberFree + " thang";
			}
			mSubObj.mStatus = Subscriber.Status.ActiveTrial;
		}
		else if (mPromoObj.mAdvertiseType == AdvertiseType.Promotion)
		{
			if (mPromoObj.mPromotionType == PromotionType.Day)
			{
				mCal_Expire.add(Calendar.DATE, mPromoObj.PromotionNumberFree - 1);
				FreeTime = "" + mPromoObj.PromotionNumberFree + " ngay";
			}
			else if (mPromoObj.mPromotionType == PromotionType.Week)
			{
				mCal_Expire.add(Calendar.DATE, mPromoObj.PromotionNumberFree * 7);
				FreeTime = "" + mPromoObj.PromotionNumberFree + " tuan";
			}
			else if (mPromoObj.mPromotionType == PromotionType.Month)
			{
				mCal_Expire.add(Calendar.MONTH, mPromoObj.PromotionNumberFree);
				FreeTime = "" + mPromoObj.PromotionNumberFree + " thang";
			}
			mSubObj.mStatus = Subscriber.Status.ActivePromotion;
		}

		mSubObj.ExpiryDate = mCal_Expire.getTime();
	}

	private MTType AddToList()
	{
		try
		{
			if (MSISDN.startsWith("8484"))
				return mMTType;

			MTContent = Common.GetDefineMT_Message(mMTType, FreeTime);

			// Không gửi MT cho KH khi đăng ký từ kệnh VASVOUCHER (xem kịch bản)
			if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
			{
				AddToMOLog(mMTType, "[Khong gui]" + MTContent);
			}
			else
			{
				if (Common.SendMT(MSISDN, Keyword, MTContent, RequestID))
					AddToMOLog(mMTType, MTContent);
			}

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
		return mMTType;
	}
	
	/**
	 * Lấy thông tin MO từ VNP gửi sang
	 */
	private void GetMO()
	{
		try
		{
			String[] arr = Note.split("\\|");
			if(arr.length >=2)
			{
				Keyword = arr[1];
			}
		}
		catch(Exception ex)
		{
			mLog.log.error(ex);
		}
	}
	

	private void Init() throws Exception
	{
		try
		{
			mSub = new Subscriber(LocalConfig.mDBConfig_MSSQL);
			mUnSub = new UnSubscriber(LocalConfig.mDBConfig_MSSQL);
			mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
			mKeyword = new Keyword(LocalConfig.mDBConfig_MSSQL);

			mTable_MOLog = CurrentData.GetTable_MOLog();
			mTable_Sub = CurrentData.GetTable_Sub();

			mCal_Expire.set(Calendar.MILLISECOND, 0);
			mCal_Expire.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
					mCal_Current.get(Calendar.DATE), 23, 59, 59);

		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private void AddToMOLog(MTType mMTType_Current, String MTContent_Current) throws Exception
	{
		try
		{
			MOObject mMOObj = new MOObject(MSISDN, mChannel, mMTType_Current, Keyword, MTContent_Current, RequestID,
					PID, mCal_Current.getTime(), Calendar.getInstance().getTime(), mVNPApp, UserName, IP,
					mSubObj.PartnerID);

			mTable_MOLog = mMOObj.AddNewRow(mTable_MOLog);
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	private void Insert_MOLog()
	{
		try
		{
			MOLog mMOLog = new MOLog(LocalConfig.mDBConfig_MSSQL);
			mMOLog.Insert(0, mTable_MOLog.GetXML());
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	protected void CreateSub(SubscriberObject.InitType mInitType) throws Exception
	{

		switch (mInitType)
		{
			case NewReg :
				mSubObj = new SubscriberObject();
				mSubObj.MSISDN = MSISDN;
				mSubObj.FirstDate = mCal_Current.getTime();
				mSubObj.ResetDate = mCal_Current.getTime();
				mSubObj.EffectiveDate = mCal_Current.getTime();
				mSubObj.ExpiryDate = mCal_Expire.getTime();

				// mSubObj.RetryChargeDate=
				// mSubObj.RetryChargeCount=
				// mSubObj.RenewChargeDate=
				mSubObj.mChannelType = mChannel;
				mSubObj.mStatus = Status.ActiveFree;
				mSubObj.PID = PID;
				// mSubObj.LastSuggestrID=
				// mSubObj.SuggestByDay=
				// mSubObj.TotalSuggest=
				/*
				 * mSubObj.LastSuggestDate= mSubObj.AnswerForSuggestID=
				 * mSubObj.LastAnswer= mSubObj.AnswerStatusID=
				 * mSubObj.AnswerByDay= mSubObj.LastAnswerDate=
				 * mSubObj.DeregDate=
				 */
				mSubObj.PartnerID = mSubObj.PartnerID = GetPartnerID();
				mSubObj.mVNPApp = mVNPApp;
				mSubObj.UserName = UserName;
				mSubObj.IP = IP;

				break;
			case RegAgain :
				// mSubObj = new SubscriberObject();
				// mSubObj.MSISDN=mMsgObject.getUserid();
				// mSubObj.FirstDate= mCal_Current.getTime();
				if (mSubObj.IsFreeReg(90))
				{
					mSubObj.ResetDate = mCal_Current.getTime();
				}
				mSubObj.EffectiveDate = mCal_Current.getTime();
				mSubObj.ExpiryDate = mCal_Expire.getTime();

				// mSubObj.RetryChargeDate=
				// mSubObj.RetryChargeCount=
				// mSubObj.RenewChargeDate=
				mSubObj.mChannelType = mChannel;
				mSubObj.mStatus = Status.Active;
				mSubObj.PID = PID;
				mSubObj.LastSuggestrID = 0;
				mSubObj.SuggestByDay = 0;
				mSubObj.TotalSuggest = 0;
				mSubObj.LastSuggestDate = null;
				mSubObj.AnswerForSuggestID = 0;
				mSubObj.LastAnswer = "";
				mSubObj.mLastAnswerStatus = dat.history.Play.Status.Nothing;
				mSubObj.AnswerByDay = 0;
				mSubObj.LastAnswerDate = null;
				// mSubObj.DeregDate=

				mSubObj.PartnerID = mSubObj.PartnerID = GetPartnerID();
				mSubObj.mVNPApp = mVNPApp;
				mSubObj.UserName = UserName;
				mSubObj.IP = IP;
				break;
			case UndoReg :
				// mSubObj = new SubscriberObject();
				// mSubObj.MSISDN=mMsgObject.getUserid();
				// mSubObj.FirstDate= mCal_Current.getTime();
				mSubObj.ResetDate = mCal_Current.getTime();
				mSubObj.EffectiveDate = mCal_Current.getTime();
				mSubObj.ExpiryDate = mCal_Expire.getTime();

				// mSubObj.RetryChargeDate=
				// mSubObj.RetryChargeCount=
				// mSubObj.RenewChargeDate=
				mSubObj.mChannelType = mChannel;
				mSubObj.mStatus = Status.ActiveFree;
				mSubObj.LastSuggestrID = 0;
				mSubObj.SuggestByDay = 0;
				mSubObj.TotalSuggest = 0;
				mSubObj.LastSuggestDate = null;
				mSubObj.AnswerForSuggestID = 0;
				mSubObj.LastAnswer = "";
				mSubObj.mLastAnswerStatus = dat.history.Play.Status.Nothing;
				mSubObj.AnswerByDay = 0;
				mSubObj.LastAnswerDate = null;
				// mSubObj.DeregDate=

				mSubObj.PartnerID = mSubObj.PartnerID = GetPartnerID();
				mSubObj.mVNPApp = mVNPApp;
				mSubObj.UserName = UserName;
				mSubObj.IP = IP;
				break;
			default :
				break;
		}
	}

	private boolean Insert_Sub() throws Exception
	{
		try
		{
			mTable_Sub.Clear();
			mTable_Sub = mSubObj.AddNewRow(mTable_Sub);

			if (!mSub.Insert(0, mTable_Sub.GetXML()))
			{
				mLog.log.info("Insert vao table Subscriber KHONG THANH CONG: XML Insert-->" + mTable_Sub.GetXML());
				return false;
			}

			return true;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	private boolean MoveToUnSub() throws Exception
	{
		try
		{
			mTable_Sub.Clear();
			mTable_Sub = mSubObj.AddNewRow(mTable_Sub);

			if (!mSub.Move(0, mTable_Sub.GetXML()))
			{
				mLog.log.info("Move tu UnSub Sang Sub KHONG THANH CONG: XML Insert-->" + mTable_Sub.GetXML());
				return false;
			}

			return true;
		}
		catch (Exception ex)
		{
			throw ex;
		}
	}

	/**
	 * Chuyển Sản lượng sang PartnerID của HB
	 * 
	 * @param PartnerID
	 * @return
	 */
	private int GetPartnerID_Pass(int PartnerID)
	{
		try
		{
			Calendar BeginDate = Calendar.getInstance();
			Calendar EndDate = Calendar.getInstance();

			BeginDate.set(Calendar.MILLISECOND, 0);
			BeginDate.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
					mCal_Current.get(Calendar.DATE), 0, 0, 0);

			EndDate.set(Calendar.MILLISECOND, 0);
			EndDate.set(mCal_Current.get(Calendar.YEAR), mCal_Current.get(Calendar.MONTH),
					mCal_Current.get(Calendar.DATE), 23, 59, 59);

			WapRegLog mWapRegLog = new WapRegLog(LocalConfig.mDBConfig_MSSQL);
			MyTableModel mTable_Count = mWapRegLog.Select(5, "2", Integer.toString(PartnerID), MyConfig
					.Get_DateFormat_InsertDB().format(BeginDate.getTime()),
					MyConfig.Get_DateFormat_InsertDB().format(EndDate.getTime()));
			
			if (mTable_Count.GetRowCount() > 0)
			{
				int PartnetCount = Integer.parseInt(mTable_Count.GetValueAt(0, "Total").toString());
				if (PartnetCount % 10 == 2 || PartnetCount % 10 == 8)
				{
					PartnerID_Pass = 28;
					return 28;
				}
			}
			PartnerID_Pass = 0;
			return PartnerID;
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
			return PartnerID;
		}

	}
	
	private int GetPartnerID() throws Exception
	{
		if (Common.GetApplication(AppName).mApp == VNPApplication.TelcoApplication.MOBILE_ADS
				|| Common.GetApplication(AppName).mApp == VNPApplication.TelcoApplication.MOBILEADS)
		{
			WapRegLog mWapRegLog = new WapRegLog(LocalConfig.mDBConfig_MSSQL);
			mTable_WapRegLog = mWapRegLog.Select(2, mSubObj.MSISDN);
			if (mTable_WapRegLog != null && mTable_WapRegLog.GetRowCount() > 0)
			{
				return GetPartnerID_Pass(Integer.parseInt(mTable_WapRegLog.GetValueAt(0, "PartnerID").toString()));
			}
		}
		return 0;
	}

	private void Update_WapRegLog()
	{
		try
		{
			if (Common.GetApplication(AppName).mApp == VNPApplication.TelcoApplication.MOBILE_ADS
					|| Common.GetApplication(AppName).mApp == VNPApplication.TelcoApplication.MOBILEADS)
			{

				if (mTable_WapRegLog == null || mTable_WapRegLog.GetRowCount() < 1)
					return;

				mTable_WapRegLog.SetValueAt(MyConfig.Get_DateFormat_InsertDB().format(mCal_Current.getTime()), 0,
						"RegisterDate");
				mTable_WapRegLog.SetValueAt(WapRegLog.Status.Registered.GetValue(), 0, "StatusID");

				if (PartnerID_Pass > 0)
				{
					mTable_WapRegLog.SetValueAt("PartnerID_Pass:" + Integer.toString(PartnerID_Pass), 0, "Note");
				}
				
				WapRegLog mWapRegLog = new WapRegLog(LocalConfig.mDBConfig_MSSQL);
				mWapRegLog.Update(1, mTable_WapRegLog.GetXML());
			}
		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
		}
	}

	public MTType Process()
	{
		mMTType = MTType.RegFail;
		try
		{
			// Khoi tao
			Init();

			GetMO();
			
			// Tính toàn khuyến mãi
			CalculatePromotion();

			// Lấy thông tin khách hàng đã đăng ký
			MyTableModel mTable_Sub = mSub.Select(2, PID.toString(), MSISDN);

			mSubObj = SubscriberObject.Convert(mTable_Sub, false);

			if (mSubObj.IsNull())
			{
				mTable_Sub = mUnSub.Select(2, PID.toString(), MSISDN);

				if (mTable_Sub.GetRowCount() > 0)
					mSubObj = SubscriberObject.Convert(mTable_Sub, true);
			}

			mSubObj.PID = PID;

			// Nếu đã đăng ký rồi và tiếp tục đăng ký
			if (!mSubObj.IsNull() && mSubObj.IsDereg == false)
			{
				// Kiểm tra còn free hay không
				if (mSubObj.mStatus == Status.ActiveFree || mSubObj.mStatus == Status.ActiveTrial
						|| mSubObj.mStatus == Status.ActiveBundle || mSubObj.mStatus == Status.ActivePromotion)
				{
					mMTType = MTType.RegRepeatFree;
					return AddToList();
				}
				else
				{
					mMTType = MTType.RegRepeatNotFree;
					return AddToList();
				}
			}

			// nếu được quảng cáo Bundel, trial hoặc promotion
			if (mPromoObj.mAdvertiseType != AdvertiseType.Normal)
			{
				// Tiến hành xử lý đăng ký khuyến mãi
				// Tạo dữ liệu cho đăng ký mới
				if (mSubObj.IsNull())
				{
					CreateSub(InitType.NewReg);
					SetPromotionToSub();

					ErrorCode mResult = mCharge.ChargeRegFree(mSubObj, mChannel, "DK Free");

					if (mResult != ErrorCode.ChargeSuccess)
					{
						mMTType = MTType.RegFail;
						return AddToList();
					}

					if (Insert_Sub())
					{
						if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.CCOS)
						{
							mMTType = MTType.RegCCOSSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASDEALER)
						{
							mMTType = MTType.RegVASDealerSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
						{
							mMTType = MTType.RegVASVoucherSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILE_ADS ||
								mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILEADS)
						{
							mMTType = MTType.RegMOBILEADSSuccessFree;
						}
						else
						{
							mMTType = MTType.RegNewSuccess;
						}
					}
					else
					{
						mMTType = MTType.RegFail;
					}

					return AddToList();
				}
				else if (mSubObj.IsDereg && mSubObj.mStatus == Subscriber.Status.UndoSub)
				{
					CreateSub(InitType.UndoReg);
					SetPromotionToSub();

					ErrorCode mResult = mCharge.ChargeRegFree(mSubObj, mChannel, "DK Free");

					if (mResult != ErrorCode.ChargeSuccess)
					{
						mMTType = MTType.RegFail;
						return AddToList();
					}
					if (MoveToUnSub())
					{
						if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.CCOS)
						{

							mMTType = MTType.RegCCOSSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASDEALER)
						{
							mMTType = MTType.RegVASDealerSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
						{
							mMTType = MTType.RegVASVoucherSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILE_ADS ||
								mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILEADS)
						{
							mMTType = MTType.RegMOBILEADSSuccessFree;
						}
						else
						{
							mMTType = MTType.RegNewSuccess;
						}
					}
					else
					{
						mMTType = MTType.RegFail;
					}

					return AddToList();
				}
				else
				{
					// đã từng sử dụng dịch vụ trước đây
					CreateSub(InitType.RegAgain);

					SetPromotionToSub();

					ErrorCode mResult = mCharge.ChargeRegFree(mSubObj, mChannel, "DK Free");

					if (mResult != ErrorCode.ChargeSuccess)
					{
						mMTType = MTType.RegFail;
						return AddToList();
					}
					if (MoveToUnSub())
					{
						if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.CCOS)
						{

							mMTType = MTType.RegCCOSSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASDEALER)
						{
							mMTType = MTType.RegVASDealerSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
						{
							mMTType = MTType.RegVASVoucherSuccessFree;
						}
						else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILE_ADS ||
								mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILEADS)
						{
							mMTType = MTType.RegMOBILEADSSuccessFree;
						}
						else
						{
							mMTType = MTType.RegNewSuccess;
						}
					}
					else
					{
						mMTType = MTType.RegFail;
					}

					return AddToList();
				}
			}

			// Đăng ký mới (chưa từng đăng ký trước đây)
			if (mSubObj.IsNull())
			{
				// Tạo dữ liệu cho đăng ký mới
				CreateSub(InitType.NewReg);

				ErrorCode mResult = mCharge.ChargeRegFree(mSubObj, mChannel, "DK Free");

				if (mResult != ErrorCode.ChargeSuccess)
				{
					mMTType = MTType.RegFail;
					return AddToList();
				}

				if (Insert_Sub())
				{
					if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.CCOS)
					{

						mMTType = MTType.RegCCOSSuccessFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASDEALER)
					{
						mMTType = MTType.RegVASDealerSuccessFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
					{
						mMTType = MTType.RegVASVoucherSuccessFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILE_ADS ||
							mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILEADS)
					{
						mMTType = MTType.RegMOBILEADSSuccessFree;
					}
					else
					{
						mMTType = MTType.RegNewSuccess;
					}
				}
				else
				{
					mMTType = MTType.RegFail;
				}

				return AddToList();
			}

			// nếu số điện thoại đã từng đăng ký trước đây nhưng bị Vinaphone
			// Hủy thuê bao
			if (mSubObj.IsDereg && mSubObj.mStatus == Subscriber.Status.UndoSub)
			{
				CreateSub(InitType.UndoReg);

				ErrorCode mResult = mCharge.ChargeRegFree(mSubObj, mChannel, "DK Free");

				if (mResult != ErrorCode.ChargeSuccess)
				{
					mMTType = MTType.RegFail;
					return AddToList();
				}

				if (MoveToUnSub())
				{
					if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.CCOS)
					{

						mMTType = MTType.RegCCOSSuccessFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASDEALER)
					{
						mMTType = MTType.RegVASDealerSuccessFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
					{
						mMTType = MTType.RegVASVoucherSuccessFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILE_ADS ||
							mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILEADS)
					{
						mMTType = MTType.RegMOBILEADSSuccessFree;
					}
					else
					{
						mMTType = MTType.RegNewSuccess;
					}
				}
				else
				{
					mMTType = MTType.RegFail;
				}

				return AddToList();
			}

			// Đã đăng ký trước đó nhưng đang hủy
			if (mSubObj.IsDereg)
			{
				CreateSub(InitType.RegAgain);

				// đồng bộ thuê bao sang Vinpahone
				ErrorCode mResult = mCharge.ChargeReg(mSubObj, mChannel, "DK Pay");

				// Charge
				if (mResult == ErrorCode.BlanceTooLow)
				{
					mMTType = MTType.RegNotEnoughMoney;
					return AddToList();
				}
				if (mResult != ErrorCode.ChargeSuccess)
				{
					mMTType = MTType.RegFail; // Đăng ký lại nhưng mất tiền
					return AddToList();
				}

				// Nếu xóa unsub hoặc Insert sub không thành công thì thông
				// báo lỗi
				if (MoveToUnSub())
				{
					if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.CCOS)
					{
						mMTType = MTType.RegCCOSSuccessNotFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASDEALER)
					{
						mMTType = MTType.RegVASDealerSuccessNotFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.VASVOUCHER)
					{
						mMTType = MTType.RegVASVoucherSuccessNotFree;
					}
					else if (mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILE_ADS ||
							mSubObj.mVNPApp.mApp == VNPApplication.TelcoApplication.MOBILEADS)
					{
						mMTType = MTType.RegMOBILEADSSuccessNotFree;
					}
					else
					{
						mMTType = MTType.RegAgainSuccessNotFree;
					}

					return AddToList();
				}

				mMTType = MTType.RegFail; // Đăng ký lại nhưng mất tiền
				return AddToList();
			}
			mMTType = MTType.RegFail;

		}
		catch (Exception ex)
		{
			mLog.log.error(ex);
			mMTType = MTType.RegFail;
		}
		finally
		{
			// Insert vao log
			Insert_MOLog();
			Update_WapRegLog();
		}
		return AddToList();
	}
}
