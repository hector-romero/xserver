package com.mickare.xserver.commands.XServerSubCommands;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Collections;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.mickare.xserver.XServerManager;
import com.mickare.xserver.XServerPlugin;
import com.mickare.xserver.commands.SubCommand;
import com.mickare.xserver.exceptions.NotInitializedException;
import com.mickare.xserver.net.XServer;

public class Status extends SubCommand
{

	public Status(XServerPlugin plugin)
	{
		super(plugin, "status", "", "Shows the connection status of the servers.");
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		StringBuilder sb = new StringBuilder();

		try
		{

			LinkedList<XServer> servers = new LinkedList<XServer>(XServerManager.getInstance().getServers());

			Collections.sort(servers, new Comparator<XServer>() {
				@Override
				public int compare(XServer o1, XServer o2)
				{
					return o1.getName().compareTo(o2.getName());
				}
			});

			for (XServer s : servers)
			{
				sb.append("\n").append(ChatColor.RESET).append(s.getName()).append(ChatColor.GRAY).append(" : ");
				if (s.isConnected())
				{
					sb.append(ChatColor.GREEN).append("connected");
				} else
				{
					sb.append(ChatColor.RED).append("not connected");
				}
			}

		} catch (NotInitializedException e)
		{
			sb.append(ChatColor.RED).append(e.getMessage());
		}

		sender.sendMessage(sb.toString());
		return true;
	}

}
