package artix

import chisel3._
import chisel3.iotesters._
import freechips.rocketchip.config._

class TopFIFO()(implicit p: Parameters) extends SteppedHWIOTester {
	enable_scala_debug = false
  	enable_printf_debug = false
  	val device_under_test = Module(new FIFOtoDMI())
  	def freq(data: BigInt) = {
  		poke(device_under_test.io.fifo_in.valid, 1)
  		poke(device_under_test.io.fifo_in.bits.data, data)
  	}
  	def freqack = {
  		expect(device_under_test.io.fifo_in.ready, true)
  	}
  	def freqreset = {
  		poke(device_under_test.io.fifo_in.valid, 0)
  	}
  	def checkDMIWReq(data: BigInt, addr: BigInt) = {
  		expect(device_under_test.io.dmi.req.valid, true)
  		expect(device_under_test.io.dmi.req.bits.data, data)	
  		expect(device_under_test.io.dmi.req.bits.addr, addr)	
  	}
  	def checkDMIRReq(addr: BigInt) = {
  		expect(device_under_test.io.dmi.req.valid, true)
  		expect(device_under_test.io.dmi.req.bits.addr, addr)	
  	}
  	def DMIResp(data: BigInt) = { 
  		poke(device_under_test.io.dmi.resp.valid, 1)
  		poke(device_under_test.io.dmi.resp.bits.data, data)	
  	}
  	def DMIRespReset = {
  		poke(device_under_test.io.dmi.resp.valid, 0)
  	}
  	def fresp(data: BigInt) = {
  		expect(device_under_test.io.fifo_out.valid, true)
  		expect(device_under_test.io.fifo_out.bits.data, data)
  	}
  	poke(device_under_test.io.dmi.req.ready, 1)
  	poke(device_under_test.io.fifo_out.ready, 1)
 		
		freq(196L)
 	step(1)
 		freqack
 	step(1)
 		freq(10L)
 		freqack
 	step(1)
 		freq(20L)
 		freqack
 	step(1)
 		freq(30L)
 		freqack
 	step(1)
 		freq(40L)
 		freqack
 		checkDMIWReq(169090600,68)
 	step(1)
 		freqreset
 	step(1)
 		DMIResp(169090600)
 		fresp(0)
 	step(1)
		fresp(10L)
		DMIRespReset
	step(1)
		freq(8L)
 	step(1)
 		freqack
 		checkDMIRReq(8)
 	step(1)
 		DMIResp(169090600)
 		fresp(0L)
 	step(1)
 		fresp(10L)
 	step(1)
 		fresp(20L)
 	step(1)
 		fresp(30L)
 	step(1)
 		fresp(40L)
 	step(1)
		fresp(10L)
		DMIRespReset
	step(1)
}