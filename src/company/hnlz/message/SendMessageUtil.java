package company.hnlz.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.TooManyListenersException;


import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

/**
 * 
 * @author haoxl
 * @date 2019-07-15
 * @Description 发送短信工具实现类
 */

public class SendMessageUtil implements ISendMessageUtil, SerialPortEventListener {
	
	private final static String SMS_SUCESS = "SMS_SEND_SUCESS";
	private final static String SMS_FAIL = "SMS_SEND_FAIL";
	private final static String SMS_RETURN = "CMS";
	private static CommPortIdentifier portId;
	@SuppressWarnings("rawtypes")
	private static Enumeration portList;
    private InputStream inputStream;
    private OutputStream outputStream;
    private SerialPort serialPort;
    private Integer sleepTime;
	
	private String serialPortName;
	private List<Boolean> resultList = new ArrayList<Boolean>();
	
	/**
	 * 
	 * @param serialPortName:串口名称
	 * @param sleepTime:两次发送短信的间隔时间(单位:ms),建议最少为3000,越大发送失败的机率越小
	 */
	
	public SendMessageUtil(String serialPortName, Integer sleepTime) {
		this.serialPortName = serialPortName;
		this.sleepTime = sleepTime;
	}
	
	// 初始化(打开串口)
	public void init() {
		portList = CommPortIdentifier.getPortIdentifiers();
		while (portList.hasMoreElements()) {
			portId = (CommPortIdentifier)portList.nextElement();
			if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				if (portId.getName().equals(serialPortName)) {
					System.out.println(serialPortName);
					try {
						serialPort = (SerialPort)portId.open(this.getClass().getSimpleName(), 2000);
						serialPort.addEventListener((SerialPortEventListener) this);
						serialPort.notifyOnDataAvailable(true);
						serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
						inputStream = serialPort.getInputStream();
						outputStream = serialPort.getOutputStream();
					} catch (PortInUseException e) {
						e.printStackTrace();
					} catch (UnsupportedCommOperationException e) {
						e.printStackTrace();
					} catch (TooManyListenersException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					System.out.println(serialPortName + " SerialPort is open.");
				}
			}
		}
	}
	

	/**
	 * 
	 * Description 对外调用发送短信方法
	 * @param phoneNumberList:手机号码数组
	 * @param messageContentList:短信内容数组
	 * @param callBack:回调函数接口
	 */
	
	public synchronized List<Boolean> send(List<String> phoneNumberList, List<String> messageContentList, ICallBack callBack) {
		
		judgeListIsOneOrMany(phoneNumberList, messageContentList);
		callBack.call(phoneNumberList, messageContentList, resultList);
		return resultList;
	}
	
	// 关闭并置空所有创建的对象
	public void closeResources() {
		try {
			if (serialPort != null) {
				serialPort.notifyOnDataAvailable(false);
				serialPort.removeEventListener();
				if (inputStream != null) {
					inputStream.close();
					inputStream = null;
				}
				if (outputStream != null) {
					outputStream.close();
					outputStream = null;
				}
				serialPort.close();
				serialPort = null;
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// 判断手机号码和短信内容的关系(一对一，一对多，多对一，多对多)
	private void judgeListIsOneOrMany(List<String> phoneNumberList, List<String> messageContentList) {
		
		String phoneNumber;
		String messageContent;
		if(phoneNumberList.size() == 1 && messageContentList.size() == 1) {
			phoneNumber = phoneNumberList.get(0);
			messageContent = messageContentList.get(0);
			sendMessage(phoneNumber, messageContent);
		}
		if(phoneNumberList.size() == 1 && messageContentList.size() > 1) {
			phoneNumber = phoneNumberList.get(0);
			for (String message : messageContentList) {
				sendMessage(phoneNumber, message);
			}
		}
		if(phoneNumberList.size() > 1 && messageContentList.size() == 1) {
			messageContent = messageContentList.get(0);
			for (String phoneNum : phoneNumberList) {
				sendMessage(phoneNum, messageContent);
			}
		}
		if(phoneNumberList.size() > 1 && messageContentList.size() > 1) {
			for (String phoneNum : phoneNumberList) {
				for(String message : messageContentList) {
					sendMessage(phoneNum, message);
				}
			}
		}
	}
	
	// 发送短信内部方法
	private void sendMessage(String phoneNumber, String messageContent) {
		try {
			sendDataBySerialPort(phoneNumber, messageContent);
			Thread.sleep(sleepTime);
			resultList.add(true);
			System.out.println(phoneNumber + ":" + messageContent + ":" + new Date());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// 将发送内容写入outputStream并通过串口发送
	private void sendDataBySerialPort(String phoneNumber, String messageContent) {
		
		/*
		 * 发送协议格式：
		 * 接收号码:协议类型:短信内容
		 * 13102859936:0:你好，Hello，SMS
		 * 目标手机号码：8613102859936(带时区)，13102859936(不带时区)，031185661213(座机)
		 * 编码方式：取值 ‘0’0x30：文本发送，目前支持长短消息。取值 ‘1’0x31：二进制数据
		 * 短消息内容：采用 GBK 编码方式  中文，英文，中英文混合
		 */
		
		String splitSendContent;
		splitSendContent = phoneNumber + ":" + "0" + ":" + messageContent;
		try {
			outputStream.write(splitSendContent.getBytes("GBK"), 0, splitSendContent.getBytes("GBK").length);
			outputStream.flush();
			System.out.println("Message had send.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// 监听串口得到返回信息
	@Override
	public void serialEvent(SerialPortEvent serialPortEvent) {
        switch (serialPortEvent.getEventType()) {
        	case SerialPortEvent.BI:
        	case SerialPortEvent.OE:
            case SerialPortEvent.FE:
            case SerialPortEvent.PE:
            case SerialPortEvent.CD:
            case SerialPortEvent.CTS:
            case SerialPortEvent.DSR:
            case SerialPortEvent.RI:
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY: break;
            case SerialPortEvent.DATA_AVAILABLE:
            	try{
            		Thread.sleep(1001);
            		while (inputStream.available() > 0) {
            			getSendResultByInputSream();
            		}
				} catch (IOException e) {
					e.printStackTrace();
          		} catch (InterruptedException e) {
					e.printStackTrace();
				}
           		break;
        }
	}
	
	// 通过输入流读取返回信息并将字节数组转化为字符串判断返回结果
	private void getSendResultByInputSream() {
		
		/*
		 *  短信发送成功的字符串为:SMS_SEND_SUCESS\r\n\0\0
		 *  短信发送失败的字符串为:SMS_SEND_FAIL\r\n\0\0
		 *  接收短信格式:+CMS:发送者手机号码:长短消息编号+短消息内容\r\n\0\0
		 *  发送者手机号码:定长 20 字节，如果不足 20 字节，用空格（0x20）补齐。  
		 *  例如:+CMS：8613102859936 ᄂᄂᄂᄂᄂᄂᄂ xxyyzz,你好\r\n\0\0
		 */
		
		byte[] readBuffer = new byte[1024];
    	int readBufferLength = 0;
    	String readStringBuffer = null;
		
		try {
			readBufferLength = inputStream.read(readBuffer);
			readStringBuffer = new String(readBuffer, 0, readBufferLength - 4).trim();
			
			if (readStringBuffer.equals(SMS_SUCESS)) {
				
				// 因接收结果属于监听异步处理，所以暂时不接收实际发送结果
				//resultList.add(true);
				System.out.println("Send sucess！");
			}
			if (readStringBuffer.equals(SMS_FAIL)) {
				//resultList.add(false);
				System.out.println("Send failed！");
			}
			if (readStringBuffer.contains(SMS_RETURN)) {
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
		
}
