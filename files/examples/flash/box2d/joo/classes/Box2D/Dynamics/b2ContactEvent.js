joo.classLoader.prepare(/*
* Copyright (c) 2010 Adam Newgas http://www.boristhebrave.com
*
* This software is provided 'as-is', without any express or implied
* warranty.  In no event will the authors be held liable for any damages
* arising from the use of this software.
* Permission is granted to anyone to use this software for any purpose,
* including commercial applications, and to alter it and redistribute it
* freely, subject to the following restrictions:
* 1. The origin of this software must not be misrepresented; you must not
* claim that you wrote the original software. If you use this software
* in a product, an acknowledgment in the product documentation would be
* appreciated but is not required.
* 2. Altered source versions must be plainly marked as such, and must not be
* misrepresented as being the original software.
* 3. This notice may not be removed or altered from any source distribution.
*/

"package Box2D.Dynamics",/*
{
	import Box2D.Dynamics.Contacts.b2Contact
	import flash.events.Event*/
	
	"public class b2ContactEvent extends flash.events.Event",2,function($$private){;return[
	
		"public var",{ contact/*:b2Contact*/:null},
		
		"function b2ContactEvent",function b2ContactEvent$(type/*:String*/)
		{
			this.super$2(type);
		},
		
		"override public function clone",function clone()/*:flash.events.Event*/ 
		{
			var event/*:b2ContactEvent*/ = new Box2D.Dynamics.b2ContactEvent(this.type);
			event.contact = this.contact;
			return event;
		},
	];},[],["flash.events.Event"], "0.8.0", "0.8.1"
	
);