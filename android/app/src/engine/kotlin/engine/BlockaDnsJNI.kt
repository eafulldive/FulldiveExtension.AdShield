/*
 * This file is part of Blokada.
 *
 * Blokada is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Blokada is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Blokada.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright © 2020 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package com.blocka.dns

class BlockaDnsJNI {
    companion object {
        external fun create_new_dns(
            listen_addr: String,
            dns_ips: String,
            dns_port: Char,
            dns_name: String,
            dns_path: String,
            use_doh: Boolean
        ): Long

        external fun dns_close(
            handle: Long
        )

        external fun engine_logger(level: String)
    }
}
