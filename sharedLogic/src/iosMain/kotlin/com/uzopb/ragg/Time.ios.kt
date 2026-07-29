package com.uzopb.ragg

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun epochNowMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
