package open_soc_debug

import Chisel._

class SoftwareTrace extends DebugModuleBundle {
  val id    = UInt(width=16)
  val value = UInt(width=regWidth)
}

class SoftwareTraceIO extends DebugModuleIO {
  val trace = (new ValidIO(new SoftwareTrace)).flip
}

class SoftwareTracer extends DebugModuleModule {
  val io = new SoftwareTraceIO
}

class RocketSoftwareTraceIO extends DebugModuleIO {
  val retire = Bool(INPUT)
  val pc = UInt(width=regWidth)
  val reg_wdata = UInt(width=regWidth)
  val reg_waddr = UInt(width=regAddrWidth)
  val reg_wen = Bool(INPUT)
  val csr_wdata = UInt(width=regWidth)
  val csr_waddr = UInt(width=csrAddrWidth)
  val csr_wen = Bool(INPUT)
}

class RocketSoftwareTracer(latch:Boolean = false) extends DebugModuleModule {
  val io = new RocketSoftwareTraceIO

  val tracer = Module(new SoftwareTracer)
  io.net <> tracer.io.net
  tracer.io.trace.valid := Bool(false)

  def input_latch[T <: Data](in:T):T = if(latch) Reg(next=in) else in

  val retire_m    = input_latch(io.retire)
  val pc_m        = input_latch(io.pc)
  val reg_wdata_m = input_latch(io.reg_wdata)
  val reg_waddr_m = input_latch(io.reg_waddr)
  val reg_wen_m   = input_latch(io.reg_wen)
  val csr_wdata_m = input_latch(io.csr_wdata)
  val csr_waddr_m = input_latch(io.csr_waddr)
  val csr_wen_m   = input_latch(io.csr_wen)

  val user_reg   = RegEnable(reg_wdata_m, retire_m && reg_wen_m && reg_waddr_m === stmUserRegAddr)
  val thread_ptr = RegEnable(reg_wdata_m, retire_m && reg_wen_m && reg_waddr_m === stmThreadPtrAddr)

  // change of thread pointer
  when(retire_m && reg_wen_m && reg_waddr_m === stmThreadPtrAddr) {
    tracer.io.trace.valid := Bool(true)
    tracer.io.trace.bits.value := thread_ptr
    tracer.io.trace.bits.id := UInt(stmCsrAddr)
  }

  // a software trace is triggered
  when(csr_wen_m && csr_waddr_m === UInt(stmCsrAddr)) {
    tracer.io.trace.valid := Bool(true)
    tracer.io.trace.bits.value := user_reg
    tracer.io.trace.bits.id := csr_waddr_m
  }
}
