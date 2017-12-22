package zynq

import freechips.rocketchip.config._
import chisel3._
import chisel3.util._
import zynq._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import Common._
import Common.Util._


class MemAccessToTL(num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit p: Parameters) extends LazyModule {
   val masterInstr = TLClientNode(Seq(TLClientPortParameters(clients = Seq(TLClientParameters(name = s"Core Instr")))))
   val masterData = TLClientNode(Seq(TLClientPortParameters(clients = Seq(TLClientParameters(name = s"Core Data")))))
   val masterDebug = TLClientNode(Seq(TLClientPortParameters(clients = Seq(TLClientParameters(name = s"Debug MemAccess")))))
   lazy val module = new MemAccessToTLModule(this,num_core_ports)
}

class MemAccessToTLBundle(outer: MemAccessToTL,num_core_ports: Int)(implicit p: Parameters) extends Bundle(){
   val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = p(xprlen))) )
   val debug_port = Flipped(new MemPortIo(data_width = p(xprlen)))  
}

class MemAccessToTLModule(outer: MemAccessToTL,num_core_ports: Int, num_bytes: Int = (1 << 21))(implicit p: Parameters) extends LazyModuleImp(outer) with Common.MemoryOpConstants
{
   val io = IO(new MemAccessToTLBundle(outer,num_core_ports))

   val tl_debug = outer.masterDebug.out.head._1
   val tl_data = outer.masterData.out.head._1
   val tl_instr = outer.masterInstr.out.head._1

   ///////////// IPORT
   if (num_core_ports == 2){
      tl_instr.d.ready := true.B
      tl_instr.a.bits.address := (io.core_ports(IPORT).req.bits.addr & "h1FFFFF".U) | p(ExtMem).base.U
      tl_instr.a.valid := io.core_ports(IPORT).req.valid 
      tl_instr.a.bits.data := io.core_ports(IPORT).req.bits.data
      tl_instr.a.bits.opcode := TLMessages.Get
      tl_instr.a.bits.size := 2.U
      tl_instr.a.bits.mask := 15.U 
      io.core_ports(IPORT).req.ready := tl_instr.a.ready
      io.core_ports(IPORT).resp.valid := tl_instr.d.valid
      io.core_ports(IPORT).resp.bits.data := tl_instr.d.bits.data
   }
   /////////////////

   /////////// DPORT 
   tl_data.d.ready := io.core_ports(DPORT).resp.ready
   tl_data.a.valid := io.core_ports(DPORT).req.valid 
   io.core_ports(DPORT).resp.valid := tl_data.d.valid
   io.core_ports(DPORT).req.ready := tl_data.a.ready 
   tl_data.a.bits.address := Mux(io.core_ports(DPORT).req.bits.addr(31),(io.core_ports(DPORT).req.bits.addr & "h1FFFFF".U) | p(ExtMem).base.U,
      io.core_ports(DPORT).req.bits.addr)

   val wire_typd = Wire(UInt(3.W))
   val reg_typd = Reg(UInt(3.W))
   reg_typd := io.core_ports(DPORT).req.bits.typ
   wire_typd := io.core_ports(DPORT).req.bits.typ
   val req_addr_lo = io.core_ports(DPORT).req.bits.addr(1,0)
   val reg_req_addr_lo = Reg(next = req_addr_lo)
   val resp_datai = tl_data.d.bits.data >> (reg_req_addr_lo << 3.U)
   val req_wdata = io.core_ports(DPORT).req.bits.data

   io.core_ports(DPORT).resp.bits.data := MuxCase(resp_datai,Array(
      (reg_typd === MT_B) -> Cat(Fill(24,resp_datai(7)),resp_datai(7,0)), 
      (reg_typd === MT_H) -> Cat(Fill(16,resp_datai(15)),resp_datai(15,0)),
      (reg_typd === MT_BU) -> Cat(Fill(24,0.U),resp_datai(7,0)), 
      (reg_typd === MT_HU) -> Cat(Fill(16,0.U),resp_datai(15,0))
   ))

   when(io.core_ports(DPORT).req.bits.fcn === M_XWR){
      tl_data.a.bits.opcode := Mux((wire_typd === MT_W || wire_typd === MT_WU),TLMessages.PutFullData,TLMessages.PutPartialData)
   }
   when(io.core_ports(DPORT).req.bits.fcn === M_XRD){
      tl_data.a.bits.opcode := TLMessages.Get
   }
   tl_data.a.bits.mask := MuxCase(15.U,Array(
      (wire_typd === MT_B || wire_typd === MT_BU) -> (1.U << req_addr_lo), 
      (wire_typd === MT_H || wire_typd === MT_HU) -> (3.U << req_addr_lo)))
   tl_data.a.bits.size := MuxCase(2.U,Array(
      (wire_typd === MT_B || wire_typd === MT_BU) -> 0.U, 
      (wire_typd === MT_H || wire_typd === MT_HU) -> 1.U))
   
   tl_data.a.bits.data := Mux((wire_typd === MT_B || wire_typd === MT_H),req_wdata << (req_addr_lo << 3.U),req_wdata)
   /////////////////
   
   // DEBUG PORT-------
   io.debug_port.req.ready := tl_debug.a.ready 
   io.debug_port.resp.valid := tl_debug.d.valid 
   // asynchronous read
   tl_debug.a.bits.address := (io.debug_port.req.bits.addr & "h1FFFFF".U) | p(ExtMem).base.U
   tl_debug.a.valid := io.debug_port.req.valid
   tl_debug.d.ready := true.B
   tl_debug.a.bits.size := 2.U
   tl_debug.a.bits.mask := 15.U
   io.debug_port.resp.bits.data := tl_debug.d.bits.data
   tl_debug.a.bits.opcode := Mux((io.debug_port.req.bits.fcn === M_XWR),TLMessages.PutPartialData,TLMessages.Get)
   when (io.debug_port.req.valid && io.debug_port.req.bits.fcn === M_XWR)
   {
      tl_debug.a.bits.address := (io.debug_port.req.bits.addr & "h1FFFFF".U) | p(ExtMem).base.U
      tl_debug.a.bits.data := io.debug_port.req.bits.data 
   }
}