package api.process;

import java.util.Calendar;
import java.util.Date;

import uti.utility.MyConfig;
import uti.utility.MyConvert;
import uti.utility.MyDate;
import uti.utility.MyLogger;
import dat.history.ChargeLog;
import dat.sub.Subscriber;
import dat.sub.UnSubscriber;
import db.define.MyTableModel;

public class ProGetTransaction
{

	public enum InfoTranResult
	{
		// 0 Đăng ký thành công dịch vụ
		Success(0),

		// 1xx Đều là đăng ký không thành công
		Fail(100),
		// Lỗi hệ thống
		SystemError(101),
		// Thông tin nhập vào không hợp lệ
		InputInvalid(102);

		private int value;

		private InfoTranResult(int value)
		{
			this.value = value;
		}

		public Integer GetValue()
		{
			return this.value;
		}

		public static InfoTranResult FromInt(int iValue)
		{
			for (InfoTranResult type : InfoTranResult.values())
			{
				if (type.GetValue() == iValue)
					return type;
			}
			return Fail;
		}
	}

	MyLogger mLog = new MyLogger(LocalConfig.LogConfigPath, this.getClass().toString());

	Subscriber mSub = null;
	UnSubscriber mUnSub = null;

	String MSISDN = "";
	String RequestID = "";
	String Channel = "";
	Date fromdate = null;
	Date todate = null;
	Integer pagesize = 10;
	Integer pageindex = 1;

	Integer TotalPage = 0;
	String AppName = "";
	String UserName = "";
	String IP = "";
	
	public ProGetTransaction(String MSISDN, String RequestID, String Channel, Date fromdate, Date todate, Integer pagesize, Integer pageindex, String AppName, String UserName, String IP)
	{
		this.MSISDN = MSISDN;
		this.RequestID = RequestID;
		this.Channel = Channel.toUpperCase().trim();
		this.fromdate = fromdate;
		this.todate = todate;
		this.pagesize = pagesize;
		this.pageindex = pageindex;
		this.AppName = AppName;
		this.UserName = UserName;
		this.IP = IP;
	}

	public String Process()
	{
		String ListLog = "";
		InfoTranResult mInfoTranResult = InfoTranResult.Fail;

		try
		{
			if (fromdate == null)
			{
				fromdate = MyDate.GetFirstDayOfMonth();
			}
			if (todate == null)
			{
				todate = (Date) Calendar.getInstance().getTime();
			}

			ChargeLog mChargeLog = new ChargeLog(LocalConfig.mDBConfig_MSSQL);

			Integer PID = MyConvert.GetPIDByMSISDN(MSISDN,LocalConfig.MAX_PID);

			Integer Total = mChargeLog.Total(0,pageindex * pagesize, pageindex * pagesize + pagesize, MSISDN, PID,Common.GetChannelType(Channel).GetValue(), fromdate,
					todate);
			MyTableModel mTable = null;
			if (Total > 0)
			{
				TotalPage = Total % pagesize == 0 ? Total / pagesize : Total / pagesize + 1;
				mTable = mChargeLog.Search(0, pageindex * pagesize, pageindex * pagesize + pagesize, MSISDN, PID, Common.GetChannelType(Channel).GetValue(), fromdate,
						todate);
			}
			if (mTable == null || mTable.GetRowCount() < 1)
			{
				mInfoTranResult = InfoTranResult.Success;
				String Format = "<RESPONSE><ERRORID>%s</ERRORID><ERRORDESC>%s</ERRORDESC><TOTALPAGE>%s</TOTALPAGE><TRANSACTION></TRANSACTION></RESPONSE>";
				ListLog = String.format(Format, new Object[] { mInfoTranResult.GetValue().toString(), mInfoTranResult.toString(), TotalPage.toString() });
			}
			else
			{
				mInfoTranResult = InfoTranResult.Success;
				StringBuilder mBuilder = new StringBuilder("");
				String Format_Item = "<ITEM><DATETIME>%s</DATETIME><INFO>%s</INFO></ITEM>";
				for (int i = 0; i < mTable.GetRowCount(); i++)
				{
					String LogDate =MyConfig.Get_DateFormat_yyyymmddhhmmss().format(MyConfig.Get_DateFormat_InsertDB().parse(mTable.GetValueAt(0, "ChargeDate").toString()));
					
					String Info = "Charge|Service:"+LocalConfig.ServiceName+"|Reason:"+mTable.GetValueAt(i, "ChargeTypeName").toString()+"|Price:"+mTable.GetValueAt(i, "Price").toString()+"|Result:"+mTable.GetValueAt(i, "ChargeStatusName").toString();
					mBuilder.append(String.format(Format_Item, new Object[] {LogDate,Info}));
				}
				String Format = "<RESPONSE><ERRORID>%s</ERRORID><ERRORDESC>%s</ERRORDESC><TOTALPAGE>%s</TOTALPAGE><TRANSACTION>"+mBuilder.toString()+"</TRANSACTION></RESPONSE>";
				ListLog = String.format(Format, new Object[] { mInfoTranResult.GetValue().toString(), mInfoTranResult.toString(), TotalPage.toString()});
			}

		}
		catch (Exception ex)
		{
			mInfoTranResult = InfoTranResult.SystemError;

			String Format = "<RESPONSE><ERRORID>%s</ERRORID><ERRORDESC>%s</ERRORDESC><TOTALPAGE>%s</TOTALPAGE><TRANSACTION></TRANSACTION></RESPONSE>";
			ListLog = String.format(Format, new Object[] { mInfoTranResult.GetValue().toString(), mInfoTranResult.toString(), TotalPage.toString() });
			mLog.log.error(ex);
		}

		return "<?xml version=\"1.0\" encoding=\"utf-8\" ?><RESPONSE>" + ListLog + "</RESPONSE>";
	}

}