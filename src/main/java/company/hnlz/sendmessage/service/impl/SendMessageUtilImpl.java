package company.hnlz.sendmessage.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import company.hnlz.sendmessage.service.ICallBack;
import company.hnlz.sendmessage.service.ISendMessageUtil;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 * 
 * author haoxl
 * date 2019-07-15
 * Description 发送短信工具实现类
 */
 
public class SendMessageUtilImpl implements ISendMessageUtil, SerialPortEventListener {
	
	private final static String SMS_SUCESS = "SMS_SEND_SUCESS";
	private final static String SMS_FAIL = "SMS_SEND_FAIL";
	private final static String SMS_RETURN = "CMS";
	private static CommPortIdentifier portId;
	@SuppressWarnings("rawtypes")
	private static Enumeration portList;
    private InputStream inputStream;
    private OutputStream outputStream;
    private SerialPort serialPort;
    private boolean initResult;
    private boolean initFlag;
    
    private LinkedBlockingQueue<String> sendMessageQueue = new LinkedBlockingQueue<String>();
    
	private ICallBack callBack;
	private String phoneNumber;
	private String messageContent;
	
	private Integer sleepTime;
	
	
	/**
	 * author haoxl
     * date 2019-08-12
	 * Description 单例模式
	 */
	
	private volatile static SendMessageUtilImpl sendMessageUtil;  
	
    private SendMessageUtilImpl () {}
    
    public static SendMessageUtilImpl getSingleton () {
    	if (sendMessageUtil == null) {
    		synchronized (SendMessageUtilImpl.class) {
    			if (sendMessageUtil == null) {
    				sendMessageUtil = new SendMessageUtilImpl();
    			}
    		}
    	}
    	return sendMessageUtil;
    }

	
	/**
	 * author haoxl
     * date 2019-08-12
	 * Description 初始化(打开串口)
	 * param serialPortName:串口名(在Linux下使用USB线连接服务器串口名应为:/dev/ssyUSB0)
	 * param sleepTime:发送短信间隔时间(最短设置3000,否则发送失败机率会很大)
	 */
	
	public boolean init (String serialPortName, String sleepTime) {
		if (!initFlag) {
			this.sleepTime = Integer.parseInt(sleepTime);
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
							initResult = true;
							
							// 初始化成功后新建线程
							addThread();
							
							initFlag = true;
							
						} catch (Exception e) {
							e.printStackTrace();
							System.out.println(serialPortName + " SerialPort is open fail.");
							initResult = false;
						}
						System.out.println(serialPortName + " SerialPort is open.");
					}
				}
			}
		}
		
		return initResult;
	}
	
	
	/**
	 * author haoxl
     * date 2019-08-12
	 * Description 在进行初始化时新建定时器及相关线程
	 */
	
	private void addThread () {
		//创建一个定时器
        Timer timer = new Timer();
        //schedule方法是执行时间定时任务的方法
        timer.schedule(new TimerTask () {

            //run方法就是具体需要定时执行的任务
            @Override
            public void run () {
            	Iterator<String> iterator = sendMessageQueue.iterator();
				while (iterator.hasNext()) {
					String splitSendContent;
					splitSendContent = sendMessageQueue.poll();
					if (splitSendContent != null) {
						boolean result = sendMessage(splitSendContent);
						String[] sendParameter = splitSendContent.split(":",-1);
						phoneNumber = sendParameter[0];
						messageContent = sendParameter[2];
						callBack.call(phoneNumber, messageContent, result);
					}
				}
            }
        }, 0, 1000);
	}
	

	/**
	 * author haoxl
     * date 2019-07-25
	 * Description 对外调用发送短信方法
	 * param phoneNumberList:手机号码数组
	 * param messageContentList:短信内容数组
	 * param callBack:回调函数接口
	 */
	
	public void send (List<String> phoneNumberList, List<String> messageContentList, ICallBack callBack) {
		
		judgeListIsOneOrManyAndAddArgToQueue(phoneNumberList, messageContentList);
		
		this.callBack = callBack;

	}
	
	/**
	 * author haoxl
     * date 2019-07-26
	 * Description 关闭所有已打开的资源(因不需要关闭所以暂时停用)
	 */
	
	@SuppressWarnings("unused")
	private void closeResources () {
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
	
	/**
	 * author haoxl
     * date 2019-07-26
	 * Description 判断手机号码和短信内容的关系(一对一，一对多，多对一，多对多)并将手机号码和短信内容按照一定格式存入队列
	 * param phoneNumberList:手机号码数组
	 * param messageContentList:短信内容数组
	 */
	
	private void judgeListIsOneOrManyAndAddArgToQueue (List<String> phoneNumberList, List<String> messageContentList) {
		
		String phoneNumber;
		String messageContent;
		String formatParameter;
		if (phoneNumberList.size() == 1 && messageContentList.size() == 1) {
			phoneNumber = phoneNumberList.get(0);
			messageContent = messageContentList.get(0);
			formatParameter = phoneNumber + ":" + "0" + ":" + messageContent;
			sendMessageQueue.add(formatParameter);
		}
		if (phoneNumberList.size() == 1 && messageContentList.size() > 1) {
			phoneNumber = phoneNumberList.get(0);
			for (String message : messageContentList) {
				formatParameter = phoneNumber + ":" + "0" + ":" + message;
				sendMessageQueue.add(formatParameter);
			}
		}
		if (phoneNumberList.size() > 1 && messageContentList.size() == 1) {
			messageContent = messageContentList.get(0);
			for (String phoneNum : phoneNumberList) {
				formatParameter = phoneNum + ":" + "0" + ":" + messageContent;
				sendMessageQueue.add(formatParameter);
			}
		}
		if (phoneNumberList.size() > 1 && messageContentList.size() > 1) {
			for (String phoneNum : phoneNumberList) {
				for(String message : messageContentList) {
					formatParameter = phoneNum + ":" + "0" + ":" + message;
					sendMessageQueue.add(formatParameter);
				}
			}
		}
	}
	
	/**
	 * author haoxl
     * date 2019-07-25
	 * Description 发送短信内部方法
	 * param splitSendContent:发送内容的固定格式字符串
	 */	
	
	private boolean sendMessage (String splitSendContent) {
		
		
		boolean result = false;
		try {
			sendDataBySerialPort(splitSendContent);
			result = true;
			System.out.println(splitSendContent + ":" + new Date());
		} catch (Exception e) {
			result = false;
			e.printStackTrace();
		}
		return result;
	}
	
	/**
	 * author haoxl
     * date 2019-07-26
	 * Description 将发送内容写入outputStream并通过串口发送
	 * param splitSendContent:发送内容的固定格式字符串
	 */	

	private void sendDataBySerialPort (String splitSendContent) {
		
		/*
		 * 发送协议格式：
		 * 接收号码:协议类型:短信内容
		 * 13102859936:0:你好，Hello，SMS
		 * 目标手机号码：8613102859936(带时区)，13102859936(不带时区)，031185661213(座机)
		 * 编码方式：取值 ‘0’0x30：文本发送，目前支持长短消息。取值 ‘1’0x31：二进制数据
		 * 短消息内容：采用 GBK 编码方式  中文，英文，中英文混合
		 */
		
		try {
			outputStream.write(splitSendContent.getBytes("GBK"), 0, splitSendContent.getBytes("GBK").length);
			outputStream.flush();
			System.out.println("Message had send.");
			Thread.sleep(sleepTime);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * author haoxl
     * date 2019-07-25
	 * Description 监听串口得到返回信息
	 * param serialPortEvent:串口事件
	 */	
	
	public void serialEvent (SerialPortEvent serialPortEvent) {
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
            	try {
            		while (inputStream.available() > 0) {
            			getSendResultByInputSream();
            		}
				} catch (IOException e) {
					e.printStackTrace();
          		} 
           		break;
        }
	}
	
	/**
	 * author haoxl
     * date 2019-07-25
	 * Description 通过输入流读取返回信息并将字节数组转化为字符串判断返回结果
	 */	
	
	private void getSendResultByInputSream () {
		
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
