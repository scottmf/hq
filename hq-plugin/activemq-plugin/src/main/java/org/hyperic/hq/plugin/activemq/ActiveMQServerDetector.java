/**
 * NOTE: This copyright does *not* cover user programs that use HQ program
 * services by normal system calls through the application program interfaces
 * provided as part of the Hyperic Plug-in Development Kit or the Hyperic Client
 * Development Kit - this is merely considered normal use of the program, and
 * does *not* fall under the heading of "derived work".
 *
 * Copyright (C) [2010,2013], VMware, Inc. This file is part of HQ.
 *
 * HQ is free software; you can redistribute it and/or modify it under the terms
 * version 2 of the GNU General Public License as published by the Free Software
 * Foundation. This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 */
package org.hyperic.hq.plugin.activemq;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.product.jmx.MxServerDetector;

public class ActiveMQServerDetector extends MxServerDetector {

    private Log log = LogFactory.getLog(ActiveMQServerDetector.class);
    private final static String HOME = "-Dactivemq.home=";
    private final static String BASE = "-Dactivemq.base=";

    @Override
    protected List getServerProcessList() {
        List procs = new ArrayList();

        String query = "State.Name.sw=java,Args.*.sw=" + BASE;
        long[] pids = getPids(query);
        if (log.isDebugEnabled()) {
            log.debug("ptql='" + query + "' matched pids=" + asList(pids));
        }

        for (int i = 0; i < pids.length; i++) {
            long pid = pids[i];
            String[] args = getProcArgs(pid);
            String path = null;

            for (int j = 0; j < args.length; j++) {
                String arg = args[j];
                if (arg.startsWith(BASE)) {
                    int ix = arg.indexOf('=');
                    if (ix != -1) {
                        path = arg.substring(ix + 1);
                        break;
                    }
                }
            }

            if (path != null) {
                MxProcess process =
                        new MxProcess(pid,
                        args,
                        path);
                procs.add(process);
            }
        }

        return procs;
    }

    @Override
    protected boolean isInstallTypeVersion(MxProcess process) {
        String dir = null;
        for (int j = 0; j < process.getArgs().length; j++) {
            String arg = process.getArgs()[j];
            if (arg.startsWith(HOME)) { // to check the version we need to use the home path
                int ix = arg.indexOf('=');
                if (ix != -1) {
                    dir = arg.substring(ix + 1);
                    break;
                }
            }
        }

        if (dir == null) {
            return false;
        } else {
            return isInstallTypeVersion(dir);
        }
    }

    @Override
    protected String getProcQuery(String path) {
        return "State.Name.sw=java,Args.*.eq=" + BASE + path;
    }

    private List<Long> asList(long[] input) {
        List<Long> list = new ArrayList<Long>();
        for (long value : input) {
            list.add(value);
        }
        return list;
    }
}
